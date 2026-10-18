# aivideo 本地 LLM 的 native 构建（M7）。
#
# 引入 workspace 级 dependencies 下的 llama.cpp 0.7.0 源码（只读，不修改），
# 只编译 core（llama + ggml），关闭 tests / examples / tools / server / common。
#
# 关键教训（videonotes 2026-10-17 实锤）：Gradle debug 构建 → CMAKE_BUILD_TYPE=Debug
# → CMAKE_CXX_FLAGS_DEBUG 为空 → 无任何 -O 标志 → -O0，llama.cpp 实测只有 -O3 的
# 5-8% 效率（0.4 tok/s vs 40 tok/s）。必须显式加 -O3（保留 -g），否则白集成。

cmake_minimum_required(VERSION 3.22.1)

project(aivideo_llama)

# llama.cpp 源码位置：允许用环境变量覆盖，默认指向 workspace 依赖目录。
# 相对路径：app/src/main/cpp → 上 6 级到 7Z1H → dependencies/...
if(DEFINED ENV{LLAMA_CPP_DIR} AND NOT "$ENV{LLAMA_CPP_DIR}" STREQUAL "")
    set(LLAMA_CPP_DIR "$ENV{LLAMA_CPP_DIR}")
else()
    set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../dependencies/llama.cpp/llama.cpp-0.7.0")
endif()

if(NOT EXISTS "${LLAMA_CPP_DIR}/include/llama.h")
    message(FATAL_ERROR "找不到 llama.cpp 源码：${LLAMA_CPP_DIR}（可设 LLAMA_CPP_DIR 环境变量覆盖）")
endif()

# 只编 core：关掉所有附带目标
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)

# 性能关键：-O3（见文件头教训）
add_compile_options(-O3)

# videonotes 在天玑 9400 上验证过的组合：armv9-a + 关 repack → 40 tok/s
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv9-a" CACHE STRING "" FORCE)
set(GGML_CPU_REPACK OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" llama.cpp-build)

add_library(aivideo_llama SHARED llama_jni.cpp)

# llama target 的 PUBLIC include 目录（含 llama.h）会自动传递
target_link_libraries(aivideo_llama PRIVATE llama log)

# JNI 边界不抛异常
target_compile_options(aivideo_llama PRIVATE -fexceptions)
