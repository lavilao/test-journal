#include <jni.h>
#include <android/log.h>

#include <cstdio>
#include <cstring>
#include <mutex>
#include <vector>

#if NEEDLE_AVAILABLE
#include "cactus.h"
#endif

#define LOG_TAG "MnemosyneCactus"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// The Cactus engine keeps one process-global model per kind (text / speech / vision)
// and is not thread-safe: every call goes through this mutex.
static std::mutex g_needle_mutex;

// The engine reads the .cact bytes in place, so every loaded model's buffer
// must stay alive for the lifetime of the process. Heap blocks owned by these
// vectors are stable after being moved into the container.
static std::vector<std::vector<unsigned char>> g_model_buffers;

static constexpr int kOutCapacity = 64 * 1024;
static constexpr int kVisionOutCapacity = 512 * 1024;

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
 * Loads one .cact file (needle3.cact, whistle.cact, or mirage.cact — the
 * Cactus engine tells them apart by kind). Returns the bitmask of loaded
 * model kinds after the call (1 = text, 2 = speech, 4 = vision) or -1.
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

    int rc = cactus_load(buf, n);
    if (rc != 0) {
        LOGE("cactus_load failed: rc=%d err=%s", rc, cactus_last_error());
        if (!g_model_buffers.empty()) g_model_buffers.pop_back();
        return -1;
    }
    return cactus_models();
#else
    (void)env;
    (void)path;
    return -1;
#endif
}

/** Bitmask of the model kinds currently loaded (1 = text, 2 = speech, 4 = vision). */
JNIEXPORT jint JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeModels(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
#if NEEDLE_AVAILABLE
    std::lock_guard<std::mutex> lock(g_needle_mutex);
    return cactus_models();
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
    int rc = cactus_init(sys, tools, nullptr);
    env->ReleaseStringUTFChars(system_prompt, sys);
    env->ReleaseStringUTFChars(tools_json, tools);
    if (rc < 0) {
        LOGE("cactus_init failed: rc=%d err=%s", rc, cactus_last_error());
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
    int rc = cactus_complete(cinput, nullptr, 0, max_new_tokens, out.data(), kOutCapacity);
    env->ReleaseStringUTFChars(input, cinput);
    if (rc < 0) {
        LOGE("cactus_complete(text) failed: rc=%d err=%s", rc, cactus_last_error());
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
                                                             jstring keywords,
                                                             jint max_new_tokens) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (pcm == nullptr || samples <= 0) return nullptr;
    std::vector<float> audio(static_cast<size_t>(samples));
    env->GetFloatArrayRegion(pcm, 0, samples, audio.data());

    const char *lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char *kw = keywords != nullptr ? env->GetStringUTFChars(keywords, nullptr) : nullptr;
    const char *kwArg = (kw != nullptr && kw[0] != '\0') ? kw : nullptr;

    std::vector<char> out(kOutCapacity);
    int rc = 0;
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        cactus_set_audio(lang, kwArg, 0);
        rc = cactus_complete(nullptr, audio.data(), samples, max_new_tokens,
                             out.data(), kOutCapacity);
    }
    if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
    if (kw != nullptr) env->ReleaseStringUTFChars(keywords, kw);
    if (rc < 0) {
        LOGE("cactus_complete(audio) failed: rc=%d err=%s", rc, cactus_last_error());
        return nullptr;
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    (void)env;
    (void)pcm;
    (void)samples;
    (void)language;
    (void)keywords;
    (void)max_new_tokens;
    return nullptr;
#endif
}

/** Transcribes 16 kHz mono float PCM with the speech model; raw JSON or null. */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeTranscribe(JNIEnv *env, jobject thiz,
                                                          jfloatArray pcm,
                                                          jint samples,
                                                          jstring language,
                                                          jstring keywords) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (pcm == nullptr || samples <= 0) return nullptr;
    std::vector<float> audio(static_cast<size_t>(samples));
    env->GetFloatArrayRegion(pcm, 0, samples, audio.data());

    const char *lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char *kw = keywords != nullptr ? env->GetStringUTFChars(keywords, nullptr) : nullptr;
    const char *kwArg = (kw != nullptr && kw[0] != '\0') ? kw : nullptr;

    std::vector<char> out(kOutCapacity);
    int rc = 0;
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        rc = cactus_transcribe(audio.data(), samples, lang, kwArg, 0,
                               out.data(), kOutCapacity);
    }
    if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
    if (kw != nullptr) env->ReleaseStringUTFChars(keywords, kw);
    if (rc < 0) {
        LOGE("cactus_transcribe failed: rc=%d err=%s", rc, cactus_last_error());
        return nullptr;
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    (void)env;
    (void)pcm;
    (void)samples;
    (void)language;
    (void)keywords;
    return nullptr;
#endif
}

/**
 * Vision understanding with Mirage (mirage.cact): takes packed RGB uint8 bytes
 * (width * height * 3), JSON options string, language code, and newline-separated
 * biasing keywords. Returns raw JSON (description, objects, text, relations, form).
 */
JNIEXPORT jbyteArray JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeDescribeImage(JNIEnv *env, jobject thiz,
                                                             jbyteArray rgb,
                                                             jint width,
                                                             jint height,
                                                             jstring options_json,
                                                             jstring language,
                                                             jstring keywords) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (rgb == nullptr || width <= 0 || height <= 0) return nullptr;
    const jsize expectedLen = static_cast<jsize>(width) * static_cast<jsize>(height) * 3;
    if (env->GetArrayLength(rgb) != expectedLen) {
        LOGE("cactus_describe bad RGB byte length %d != %d", env->GetArrayLength(rgb), expectedLen);
        return nullptr;
    }
    std::vector<unsigned char> pixels(static_cast<size_t>(expectedLen));
    env->GetByteArrayRegion(rgb, 0, expectedLen, reinterpret_cast<jbyte *>(pixels.data()));

    const char *opts = options_json != nullptr ? env->GetStringUTFChars(options_json, nullptr) : nullptr;
    const char *optsArg = (opts != nullptr && opts[0] != '\0') ? opts : "{}";
    const char *lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char *langArg = (lang != nullptr && lang[0] != '\0') ? lang : nullptr;
    const char *kw = keywords != nullptr ? env->GetStringUTFChars(keywords, nullptr) : nullptr;
    const char *kwArg = (kw != nullptr && kw[0] != '\0') ? kw : nullptr;

    std::vector<char> out(kVisionOutCapacity);
    int rc = 0;
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        rc = cactus_describe(
            pixels.data(),
            width,
            height,
            optsArg,
            langArg,
            kwArg,
            out.data(),
            kVisionOutCapacity
        );
    }
    if (opts != nullptr) env->ReleaseStringUTFChars(options_json, opts);
    if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
    if (kw != nullptr) env->ReleaseStringUTFChars(keywords, kw);

    if (rc < 0) {
        LOGE("cactus_describe failed: rc=%d err=%s", rc, cactus_last_error());
        return nullptr;
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kVisionOutCapacity)));
#else
    (void)env;
    (void)rgb;
    (void)width;
    (void)height;
    (void)options_json;
    (void)language;
    (void)keywords;
    return nullptr;
#endif
}

/** Sets CPU thread count (0 = automatic). */
JNIEXPORT void JNICALL
Java_com_example_ai_needle_NeedleRuntime_nativeSetThreads(JNIEnv *env, jobject thiz, jint threads) {
    (void)env;
    (void)thiz;
#if NEEDLE_AVAILABLE
    std::lock_guard<std::mutex> lock(g_needle_mutex);
    cactus_set_threads(threads);
#else
    (void)threads;
#endif
}

/**
 * TEXT EMBEDDING with the loaded Needle model: returns the embedding vector
 * for a sentence (used by the semantic search / RAG layer).
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
    int dims = cactus_embed(cinput, nullptr, 0, nullptr, 0);
    if (dims <= 0) {
        LOGE("cactus_embed(size) failed: rc=%d err=%s", dims, cactus_last_error());
        env->ReleaseStringUTFChars(input, cinput);
        return nullptr;
    }
    std::vector<float> vec(static_cast<size_t>(dims));
    int written = cactus_embed(cinput, nullptr, 0, vec.data(), dims);
    env->ReleaseStringUTFChars(input, cinput);
    if (written != dims) {
        LOGE("cactus_embed(compute) failed: rc=%d err=%s", written, cactus_last_error());
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
                                                              jstring language,
                                                              jstring keywords) {
    (void)thiz;
#if NEEDLE_AVAILABLE
    if (pcm == nullptr || samples <= 0) return nullptr;
    std::vector<float> audio(static_cast<size_t>(samples));
    env->GetFloatArrayRegion(pcm, 0, samples, audio.data());

    const char *lang = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char *kw = keywords != nullptr ? env->GetStringUTFChars(keywords, nullptr) : nullptr;
    const char *kwArg = (kw != nullptr && kw[0] != '\0') ? kw : nullptr;

    std::vector<char> out(kOutCapacity);
    int rc = 0;
    {
        std::lock_guard<std::mutex> lock(g_needle_mutex);
        rc = cactus_stream_transcribe_process(audio.data(), samples, lang, kwArg,
                                              out.data(), kOutCapacity);
    }
    if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
    if (kw != nullptr) env->ReleaseStringUTFChars(keywords, kw);
    if (rc < 0) {
        LOGE("cactus_stream_transcribe_process failed: rc=%d err=%s", rc, cactus_last_error());
        return nullptr;
    }
    return toJByteArray(env, out.data(), static_cast<int>(strnlen(out.data(), kOutCapacity)));
#else
    (void)env;
    (void)pcm;
    (void)samples;
    (void)language;
    (void)keywords;
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
        int rc = cactus_stream_transcribe_stop(out.data(), kOutCapacity);
        if (rc < 0) {
            LOGE("cactus_stream_transcribe_stop failed: rc=%d err=%s", rc, cactus_last_error());
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
    const char *err = cactus_last_error();
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
    cactus_reset();
    g_model_buffers.clear();
#endif
}

} // extern "C"
