cmake_minimum_required(VERSION 3.22.1)
project(mnemosyne_needle LANGUAGES CXX)

# JNI bridge for the Cactus Needle 3 on-device tool-calling model.
#
# The prebuilt static engine from Hugging Face (Cactus-Compute/needle3) only
# ships Android variants for armeabi-v7a and arm64-v8a. On any other ABI
# (x86_64 for emulators, riscv64) the bridge still builds, but as a stub whose
# nativeAvailable() reports false, so the Kotlin layer degrades gracefully.

add_library(needlejni SHARED needle_jni.cpp)

set(NEEDLE_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libneedle.a")

if (EXISTS "${NEEDLE_PREBUILT}")
    target_compile_definitions(needlejni PRIVATE NEEDLE_AVAILABLE=1)
    target_include_directories(needlejni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
    # The engine was built against the NDK C++ runtime (its symbols live in
    # the __ndk1 libc++ namespace), so link the static STL into this .so.
    # No STL types cross the library boundary: the needle API is plain C.
    target_link_libraries(needlejni "${NEEDLE_PREBUILT}" c++_static log)
else()
    target_compile_definitions(needlejni PRIVATE NEEDLE_AVAILABLE=0)
    target_link_libraries(needlejni c++_static log)
endif()
