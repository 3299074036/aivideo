# 影译 ProGuard / R8 规则（release 启用混淆时使用；M1 release 暂未启用混淆）

# FFmpegKit：JNI native 方法 + 同步/异步执行 API，禁止混淆
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
