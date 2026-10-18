// aivideo 本地 LLM 的 JNI 桥（M7）：llama.cpp 的最小可用封装。
//
// 只做四件事：加载模型 / 套 chat template / 流式生成 / 卸载。
// 线程模型：generate 在调用线程同步执行，逐 token 回调 Kotlin，并检查取消标志。

#include <jni.h>

#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include <android/log.h>

#include "llama.h"

#define LOG_TAG "aivideo_llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaHandle {
    llama_model* model = nullptr;
    const llama_vocab* vocab = nullptr;
    llama_context* ctx = nullptr;
    std::atomic<bool> cancel{false};
};

// generate 调用线程的最近一次错误（中文）。
thread_local std::string g_lastError;

jstring newUtf8String(JNIEnv* env, const char* data, int len) {
    // 不用 NewStringUTF：token 碎片可能含 4 字节 UTF-8，NewStringUTF 处理不了；
    // 走 byte[] + String(bytes, "UTF-8") 最稳。
    jbyteArray arr = env->NewByteArray(len);
    if (!arr) return nullptr;
    env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(data));
    jclass strClass = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    jstring result = static_cast<jstring>(env->NewObject(strClass, ctor, arr, charset));
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(charset);
    return result;
}

bool decodeBatch(llama_context* ctx, const llama_token* tokens, int n) {
    llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(tokens), n);
    return llama_decode(ctx, batch) == 0;
}

// 返回 [data, data+len) 中"完整 UTF-8 字符前缀"的长度；
// 末尾若有被切开的多字节字符，只计到其起始处（调用方缓存剩余部分等下个 token）。
int utf8ValidPrefixLen(const char* data, int len) {
    if (len <= 0) return 0;
    int start = len - 1;
    while (start > 0 && (data[start] & 0xC0) == 0x80) --start;
    const unsigned char c = static_cast<unsigned char>(data[start]);
    int need;
    if ((c & 0x80) == 0x00) need = 1;
    else if ((c & 0xE0) == 0xC0) need = 2;
    else if ((c & 0xF0) == 0xE0) need = 3;
    else if ((c & 0xF8) == 0xF0) need = 4;
    else return start;  // 非法起始字节：保守截断
    return (start + need <= len) ? len : start;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_zola_aivideo_data_llm_local_LlamaBridge_loadModel(
    JNIEnv* env, jobject /*thiz*/, jstring jPath, jint nCtx, jint nThreads) {
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    auto* h = new LlamaHandle();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // 纯 CPU
    h->model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jPath, path);
    if (!h->model) {
        g_lastError = "模型文件加载失败（文件损坏或内存不足）";
        LOGE("loadModel: %s", g_lastError.c_str());
        delete h;
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    h->ctx = llama_init_from_model(h->model, cparams);
    if (!h->ctx) {
        g_lastError = "推理上下文创建失败（内存不足？）";
        LOGE("loadModel: %s", g_lastError.c_str());
        llama_model_free(h->model);
        delete h;
        return 0;
    }
    h->vocab = llama_model_get_vocab(h->model);
    LOGI("loadModel ok: n_ctx=%d n_threads=%d", (int)nCtx, (int)nThreads);
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT jstring JNICALL
Java_com_zola_aivideo_data_llm_local_LlamaBridge_applyChatTemplate(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jSystem, jstring jUser) {
    auto* h = reinterpret_cast<LlamaHandle*>(handle);
    if (!h || !h->model) return nullptr;
    const char* sys = env->GetStringUTFChars(jSystem, nullptr);
    const char* usr = env->GetStringUTFChars(jUser, nullptr);

    std::vector<llama_chat_message> msgs;
    if (sys && sys[0] != '\0') msgs.push_back({"system", sys});
    msgs.push_back({"user", usr ? usr : ""});

    // tmpl=nullptr 用内置模板；先 16K 试，不够再按返回长度重来
    // 注意 0.7.0 的签名：llama_chat_apply_template(tmpl, chat, n_msg, add_ass, buf, len)，无 model 参数
    std::string out(16384, '\0');
    int n = llama_chat_apply_template(nullptr, msgs.data(), msgs.size(), true,
                                      out.data(), (int)out.size());
    if (n > (int)out.size()) {
        out.assign(n, '\0');
        n = llama_chat_apply_template(nullptr, msgs.data(), msgs.size(), true,
                                      out.data(), (int)out.size());
    }
    env->ReleaseStringUTFChars(jSystem, sys);
    env->ReleaseStringUTFChars(jUser, usr);
    if (n < 0) return nullptr;
    return newUtf8String(env, out.data(), n);
}

JNIEXPORT jint JNICALL
Java_com_zola_aivideo_data_llm_local_LlamaBridge_generate(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jPrompt, jint maxTokens,
    jfloat temperature, jobject callback) {
    auto* h = reinterpret_cast<LlamaHandle*>(handle);
    if (!h || !h->ctx) {
        g_lastError = "模型未加载";
        return 1;
    }
    h->cancel.store(false);

    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    const size_t promptLen = strlen(prompt);
    // 注意：查询模式（buffer=nullptr）下 llama_tokenize 返回负的所需长度，
    // 0 或正数才表示异常（llama.cpp 约定，视记已验证）。
    int need = llama_tokenize(h->vocab, prompt, promptLen, nullptr, 0, true, true);
    if (need >= 0) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        g_lastError = "分词失败";
        return 2;
    }
    need = -need;
    std::vector<llama_token> tokens(need);
    if (llama_tokenize(h->vocab, prompt, promptLen, tokens.data(), need, true, true) < 0) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        g_lastError = "分词失败";
        return 2;
    }
    env->ReleaseStringUTFChars(jPrompt, prompt);

    llama_sampler* sampler;
    if (temperature <= 0.0f) {
        sampler = llama_sampler_init_greedy();
    } else {
        sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234));
    }

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    if (!onToken) {
        llama_sampler_free(sampler);
        g_lastError = "回调方法找不到";
        return 3;
    }

    // prefill：分批 decode
    const int nBatch = 512;
    for (int i = 0; i < (int)tokens.size() && !h->cancel.load(); i += nBatch) {
        int n = std::min(nBatch, (int)tokens.size() - i);
        if (!decodeBatch(h->ctx, tokens.data() + i, n)) {
            llama_sampler_free(sampler);
            g_lastError = "prefill 解码失败（可能显存/内存不足）";
            return 4;
        }
    }

    // decode：逐 token 采样 + 回调。
    // 注意：Qwen3 分词可能把一个汉字切成多个 token，末尾不完整的 UTF-8 序列
    // 先缓存在 pending 里，等凑成完整字符再回调，避免半个字符。
    std::string pending;
    char piece[256];
    int generated = 0;
    int rc = 0;
    while (generated < maxTokens && !h->cancel.load()) {
        llama_token tok = llama_sampler_sample(sampler, h->ctx, -1);
        if (llama_vocab_is_eog(h->vocab, tok)) break;
        if (!decodeBatch(h->ctx, &tok, 1)) {
            g_lastError = "decode 失败";
            rc = 5;
            break;
        }
        int n = llama_token_to_piece(h->vocab, tok, piece, sizeof(piece), 0, true);
        if (n > 0) {
            pending.append(piece, n);
            int valid = utf8ValidPrefixLen(pending.data(), (int)pending.size());
            if (valid > 0) {
                jstring js = newUtf8String(env, pending.data(), valid);
                if (js) {
                    env->CallVoidMethod(callback, onToken, js);
                    env->DeleteLocalRef(js);
                }
                pending.erase(0, valid);
            }
        }
        ++generated;
    }
    // flush：把最后不足一个字符的残余也发出（正常结束时 pending 应为空）
    if (rc == 0 && !pending.empty()) {
        jstring js = newUtf8String(env, pending.data(), (int)pending.size());
        if (js) {
            env->CallVoidMethod(callback, onToken, js);
            env->DeleteLocalRef(js);
        }
        pending.clear();
    }
    llama_sampler_free(sampler);

    if (h->cancel.load()) {
        g_lastError = "已取消";
        return 6;
    }
    return rc;
}

JNIEXPORT void JNICALL
Java_com_zola_aivideo_data_llm_local_LlamaBridge_requestCancel(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* h = reinterpret_cast<LlamaHandle*>(handle);
    if (h) h->cancel.store(true);
}

JNIEXPORT void JNICALL
Java_com_zola_aivideo_data_llm_local_LlamaBridge_unloadModel(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* h = reinterpret_cast<LlamaHandle*>(handle);
    if (!h) return;
    if (h->ctx) llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
}

JNIEXPORT jstring JNICALL
Java_com_zola_aivideo_data_llm_local_LlamaBridge_lastError(
    JNIEnv* env, jobject /*thiz*/) {
    return newUtf8String(env, g_lastError.data(), (int)g_lastError.size());
}

}  // extern "C"
