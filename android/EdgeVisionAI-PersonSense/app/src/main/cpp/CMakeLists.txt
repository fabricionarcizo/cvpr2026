cmake_minimum_required(VERSION 3.22.1)
project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Prebuilt libraries from pkg-snapdragon (built with OpenCL + Hexagon backends).
# Living in src/main/jniLibs/${ANDROID_ABI}/ — automatically packaged into the APK.
set(PREBUILT_LIB_DIR   ${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI})
set(PREBUILT_INC_DIR   ${CMAKE_CURRENT_LIST_DIR}/prebuilt-include)
# We deliberately no longer compile or link against llama.cpp's common/ code.
# pkg-snapdragon's libllama-common.so was built at a different commit than our
# submodule headers and the C++ struct ABI diverges (common_chat_msg,
# common_params_sampling). We use only the stable C API in llama.h and
# reimplement the few utility helpers we need directly in ai_chat.cpp.
# NOTE: the original llm-lib also pulled fallback headers from an external
# llama.cpp checkout (${LLAMA_SRC}/include etc.). To keep this tutorial
# project self-contained we bundle every header we need under
# src/main/cpp/prebuilt-include/. No external source tree required.

# Declare prebuilt shared libraries as IMPORTED targets.
foreach(name llama mtmd ggml ggml-base ggml-cpu)
    add_library(${name} SHARED IMPORTED)
    set_target_properties(${name} PROPERTIES
        IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/lib${name}.so)
endforeach()

add_library(${CMAKE_PROJECT_NAME} SHARED ai_chat.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${PREBUILT_INC_DIR})         # llama.h, mtmd.h, mtmd-helper.h, ggml*.h

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    mtmd
    ggml
    ggml-base
    ggml-cpu
    android
    log)
