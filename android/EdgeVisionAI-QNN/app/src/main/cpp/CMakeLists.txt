# CMakeLists.txt — QNN JNI bridge for EdgeVisionAI
#
# Builds the shared library `libqnn_inference_jni.so` for arm64-v8a.
#
# QNN runtime libraries (libQnnHtp.so, libQnnSystem.so, etc.) are NOT linked at
# compile-time. They are loaded dynamically at runtime via dlopen() inside
# qnn_inference_jni.cpp. The QAIRT SDK headers (copied to include/QNN/) are
# included at compile-time for correct struct layout definitions ONLY — no
# QNN import library (.a/.so) is linked. The .so files in jniLibs/arm64-v8a/
# must be present at packaging time.

cmake_minimum_required(VERSION 3.22.1)
project("qnn_inference_jni" LANGUAGES CXX)

# ── C++ standard ─────────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ── Source files ─────────────────────────────────────────────────────────────
add_library(
    qnn_inference_jni
    SHARED
    qnn_inference_jni.cpp
)

# ── Include paths ─────────────────────────────────────────────────────────────
# JNI headers come from the NDK automatically.
# QAIRT 2.46 headers are in include/ (copied from the SDK) — used for struct
# layout definitions only; no QNN import library is linked.
target_include_directories(qnn_inference_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include/QNN    # needed by System/*.h → #include "QnnDevice.h"
)

# ── System libraries provided by the Android NDK ─────────────────────────────
find_library(log-lib  log)     # __android_log_print
find_library(android-lib android)  # AAsset / ANativeWindow (unused here, good to have)

target_link_libraries(qnn_inference_jni
    ${log-lib}
    ${android-lib}
    dl          # dlopen / dlsym / dlclose
)

# ── Compiler flags ────────────────────────────────────────────────────────────
target_compile_options(qnn_inference_jni PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden     # hide all symbols except JNI_OnLoad and Java_* exports
    -O2
)
