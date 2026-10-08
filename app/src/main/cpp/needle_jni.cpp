#include <jni.h>
#include <android/log.h>

#include <cstdio>
#include <cstring>
#include <mutex>
#include <vector>

#if NEEDLE_AVAILABLE
#include "needle.h"
#endif

#define LOG_TAG "MnemosyneNeedle"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// The needle engine keeps one process-global model per kind (text / speech)
// and is not thread-safe: every call goes through this mutex.
static std::mutex g_needle_mutex;

// The engine reads the .cact bytes in place, so every loaded model's buffer
// must stay alive for the lifetime of the process. Heap blocks owned by these
// vectors are stable after being moved into the container.
static std::vector<std::vector<unsigned char>> g_model_buffers;

static constexpr int kOutCapacity = 64 * 1024;

static jbyteArray toJByteArray(JNIEnv *env, const char *data, int len) {
    if (data == nullptr || len <= 0) return nullptr;
    jbyteArray out = env->NewByteArray(len);
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, len, reinterpret_cast<const jbyte *>(data));
    return out;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeAvailable(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
#if NEEDLE_AVAILABLE
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

/**
 * Loads one .cact file (a text model such as needle3.cact or a speech model
 * such as whistle.cact — the engine tells them apart itself). Returns the
 * bitmask of loaded model kinds after the call (1 = text, 2 = speech, 3 =
 * both) or -1 on failure.
 */
JNIEXPORT jint JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeLoadModel(JNIEnv *env, jobject thiz, jstring path) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    if (cpath == nullptr) return -1;

    FILE *f = fopen(cpath, "rb");
    if (f == nullptr) {
        LOGE("fopen failed for %s", cpath);
        env->ReleaseStringUTFChars(path, cpath);
        return -1;
    }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0 || size > (512L * 1024L * 1024L)) {
        LOGE("bad model size %ld", size);
        fclose(f);
        env->ReleaseStringUTFChars(path, cpath);
        return -1;
    }
    std::vector<unsigned char> bytes(static_cast<size_t>(size));
    size_t read = fread(bytes.data(), 1, static_cast<size_t>(size), f);
    fclose(f);
    env->ReleaseStringUTFChars(path, cpath);
    if (read != static_cast<size_t>(size)) {
        LOGE("short read: %zu of %ld", read, size);
        return -1;
    }

    std::lock_guard<std::mutex> lock(g_needle_mutex);
    g_model_buffers.push_back(std::move(bytes));
    const unsigned char *buf = g_model_buffers.back().data();
    unsigned long long n = static_cast<unsigned long long>(g_model_buffers.back().size());

    int rc = needle_load(buf, n);
    if (rc != 0) {
        LOGE("needle_load failed: rc=%d err=%s", rc, needle_last_error());
        // Drop the buffer we just added: the engine rejected it.
        if (!g_model_buffers.empty()) g_model_buffers.pop_back();
        return -1;
    }
    return needle_models();
#else
    (void)env;
    (void)path;
    return -1;
#endif
}

/** Bitmask of the model kinds currently loaded (1 = text, 2 = speech). */
JNIEXPORT jint JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeModels(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
#if NEEDLE_AVAILABLE
    std::lock_guard<std::mutex> lock(g_needle_mutex);
    return needle_models();
#else
    return 0;
#endif
}

/**
 * Declares the system prompt and the tool catalogue. Returns the tokenized
 * static-prefix length on success or a negative value on failure.
 */
JNIEXPORT jint JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeInit(JNIEnv *env, jobject thiz,
                                                    jstring system_prompt,
                                                    jstring tools_json) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    const char *sys = env->GetStringUTFChars(system_prompt, nullptr);
    const char *tools = env->GetStringUTFChars(tools_json, nullptr);
    if (sys == nullptr || tools == nullptr) {
        if (sys != nullptr) env->ReleaseStringUTFChars(system_prompt, sys);
        if (tools != nullptr) env->ReleaseStringUTFChars(tools_json, tools);
        return -1;
    }
    std::lock_guard<std::mutex> lock(g_needle_mutex);
    int rc = needle_init(sys, tools, nullptr);
    env->ReleaseStringUTFChars(system_prompt, sys);
    env->ReleaseStringUTFChars(tools_json, tools);
    if (rc < 0) {
        LOGE("needle_init failed: rc=%d err=%s", rc, needle_last_error());
    }
    return rc;
#else
    (void)env;
    (void)system_prompt;
    (void)tools_json;
    return -1;
#endif
}

/** Answers a text query; returns the raw JSON response bytes or null. */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeCompleteText(JNIEnv *env, jobject thiz,
                                                            jstring input,
                                                            jint max_new_tokens) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    const char *cinput = env->GetStringUTFChars(input, nullptr);
    if (cinput == nullptr) return nullptr;
    std::vector<char> out(kOutCapacity);
    std::lock_guard<std::mutex> lock(g_needle_mutex);
    int rc = needle_complete(cinput, nullptr, 0, max_new_tokens, out.data(), kOutCapacity);
    env->ReleaseStringUTFChars(input, cinput);
    if (rc < 0) {
        LOGE("needle_complete(text) failed: rc=%d err=%s", rc, needle_last_error());
        return nullptr;
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    (void)env;
    (void)input;
    (void)max_new_tokens;
    return nullptr;
#endif
}

/**
 * Answers a spoken query: 16 kHz mono float PCM in [-1, 1]. When the speech
 * model is loaded the engine transcribes the clip itself and answers the
 * transcript, merging the speech fields under an audio_ prefix.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeCompleteAudio(JNIEnv *env, jobject thiz,
                                                             jfloatArray pcm,
                                                             jint samples,
                                                             jstring language,
                                                             jint max_new_tokens) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (pcm == nullptr || samples <= 0) return nullptr;
    std::vector<float> audio(static_cast<size_t>(samples));
    env->GetFloatArrayRegion(pcm, 0, samples, audio.data());

    const char *lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;

    std::vector<char> out(kOutCapacity);
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        needle_set_audio(lang, nullptr, 0);
        int rc = needle_complete(nullptr, audio.data(), samples, max_new_tokens,
                                 out.data(), kOutCapacity);
        if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
        if (rc < 0) {
            LOGE("needle_complete(audio) failed: rc=%d err=%s", rc, needle_last_error());
            return nullptr;
        }
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    (void)env;
    (void)pcm;
    (void)samples;
    (void)language;
    (void)max_new_tokens;
    return nullptr;
#endif
}

/** Transcribes 16 kHz mono float PCM with the speech model; raw JSON or null. */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeTranscribe(JNIEnv *env, jobject thiz,
                                                          jfloatArray pcm,
                                                          jint samples,
                                                          jstring language) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (pcm == nullptr || samples <= 0) return nullptr;
    std::vector<float> audio(static_cast<size_t>(samples));
    env->GetFloatArrayRegion(pcm, 0, samples, audio.data());

    const char *lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;

    std::vector<char> out(kOutCapacity);
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        int rc = needle_transcribe(audio.data(), samples, lang, nullptr, 0,
                                   out.data(), kOutCapacity);
        if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
        if (rc < 0) {
            LOGE("needle_transcribe failed: rc=%d err=%s", rc, needle_last_error());
            return nullptr;
        }
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    (void)env;
    (void)pcm;
    (void)samples;
    (void)language;
    return nullptr;
#endif
}

/**
 * TEXT EMBEDDING with the loaded Needle model: returns the embedding vector
 * for a sentence (used by the semantic search / RAG layer). The first C call
 * with a null output returns the float count without computing, so the buffer
 * is sized exactly before the second call computes it.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeEmbedText(JNIEnv *env, jobject thiz,
                                                          jstring input) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (input == nullptr) return nullptr;
    const char *cinput = env->GetStringUTFChars(input, nullptr);
    if (cinput == nullptr) return nullptr;

    std::lock_guard<std::mutex> lock(g_needle_mutex);
    int dims = needle_embed(cinput, nullptr, 0, nullptr, 0);
    if (dims <= 0) {
        LOGE("needle_embed(size) failed: rc=%d err=%s", dims, needle_last_error());
        env->ReleaseStringUTFChars(input, cinput);
        return nullptr;
    }
    std::vector<float> vec(static_cast<size_t>(dims));
    int written = needle_embed(cinput, nullptr, 0, vec.data(), dims);
    env->ReleaseStringUTFChars(input, cinput);
    if (written != dims) {
        LOGE("needle_embed(compute) failed: rc=%d err=%s", written, needle_last_error());
        return nullptr;
    }
    jfloatArray out = env->NewFloatArray(dims);
    if (out == nullptr) return nullptr;
    env->SetFloatArrayRegion(out, 0, dims, vec.data());
    return out;
#else
    (void)env;
    (void)input;
    return nullptr;
#endif
}

/**
 * LIVE transcription: appends ~1 s of 16 kHz mono float PCM and returns the
 * JSON of the words this pass committed plus the unconfirmed tail.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeStreamProcess(JNIEnv *env, jobject thiz,
                                                              jfloatArray pcm,
                                                              jint samples,
                                                              jstring language) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (pcm == nullptr || samples <= 0) return nullptr;
    std::vector<float> audio(static_cast<size_t>(samples));
    env->GetFloatArrayRegion(pcm, 0, samples, audio.data());

    const char *lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;

    std::vector<char> out(kOutCapacity);
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        int rc = needle_stream_transcribe_process(audio.data(), samples, lang, nullptr,
                                                  out.data(), kOutCapacity);
        if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
        if (rc < 0) {
            LOGE("needle_stream_transcribe_process failed: rc=%d err=%s", rc, needle_last_error());
            return nullptr;
        }
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    (void)env;
    (void)pcm;
    (void)samples;
    (void)language;
    return nullptr;
#endif
}

/** Ends the live stream; returns the JSON with the final committed words. */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeStreamStop(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
#if NEEDLE_AVAILABLE
    std::vector<char> out(kOutCapacity);
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        int rc = needle_stream_transcribe_stop(out.data(), kOutCapacity);
        if (rc < 0) {
            LOGE("needle_stream_transcribe_stop failed: rc=%d err=%s", rc, needle_last_error());
            return nullptr;
        }
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    return nullptr;
#endif
}

/** Last engine error, as raw UTF-8 bytes. */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeLastError(JNIEnv *env, jobject thiz) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    std::lock_guard<std::mutex> lock(g_needle_mutex);
    const char *err = needle_last_error();
    if (err == nullptr) return nullptr;
    return toJByteArray(env, err, static_cast<int>(strlen(err)));
#else
    (void)env;
    return nullptr;
#endif
}

JNIEXPORT void JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeReset(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
#if NEEDLE_AVAILABLE
    std::lock_guard<std::mutex> lock(g_needle_mutex);
    needle_reset();
    g_model_buffers.clear();
#endif
}

} // extern "C"
