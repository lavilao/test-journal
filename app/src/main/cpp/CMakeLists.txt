cmake_minimum_required(VERSION 3.22.1)
project(mnemosyne_needle LANGUAGES CXX)

# JNI bridge for the Cactus 4.1.4 unified on-device engine (Needle 3, Whistle, Mirage).
#
# The prebuilt static engine (Cactus-Compute/cactus v4.1.4) ships Android
# variants for armeabi-v7a and arm64-v8a. On any other ABI (x86_64 for
# emulators, riscv64) the bridge builds as a stub whose nativeAvailable()
# reports false, so the Kotlin layer degrades gracefully.

add_library(needlejni SHARED needle_jni.cpp)

set(CACTUS_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libcactus.a")

if (EXISTS "${CACTUS_PREBUILT}")
    target_compile_definitions(needlejni PRIVATE NEEDLE_AVAILABLE=1)
    target_include_directories(needlejni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
    # Static STL matches the prebuilt libcactus.a (__ndk1 namespace).
    target_link_libraries(needlejni "${CACTUS_PREBUILT}" c++_static log)
    # armeabi-v7a weak murmur2 shim if libc++_static omits __hash_memory.
    set(NEEDLE_HASH_SHIM "${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/hash_memory_shim.o")
    if (EXISTS "${NEEDLE_HASH_SHIM}")
        target_link_libraries(needlejni "${NEEDLE_HASH_SHIM}")
    endif()
else()
    target_compile_definitions(needlejni PRIVATE NEEDLE_AVAILABLE=0)
    target_link_libraries(needlejni c++_static log)
endif()
