/*
 * JNI bridge to eSpeak-NG for the KittenTTS 2 (pico) phoneme front end.
 *
 * KittenTTS 2's ONNX graphs take IPA phoneme token ids, not text, so the app
 * needs a grapheme-to-phoneme step. eSpeak-NG is what KittenML itself uses for
 * every language except English, and libttsespeak.so (eSpeak-NG 1.52.0, GPLv3,
 * built from https://github.com/espeak-ng/espeak-ng) ships prebuilt in
 * jniLibs/<abi>/. This file is only the thin JNI shim around it.
 *
 * On ABIs where the prebuilt library is missing (x86_64 emulators) the shim is
 * compiled as a stub: nativeInit() returns false and the Kotlin layer reports
 * that the KittenTTS engine is unavailable instead of crashing.
 */
#include <jni.h>
#include <android/log.h>

#define LOG_TAG "KittenTtsJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef KITTEN_TTS_ESPEAK

#include <string>

extern "C" {
// Minimal declarations from espeak-ng's speak_lib.h (kept local so the build
// does not need the GPL header tree).
int espeak_Initialize(int output, int buflength, const char *path, int options);
int espeak_SetVoiceByName(const char *name);
const char *espeak_TextToPhonemes(const void **textptr, int textmode, int phonememode);
}

namespace {
constexpr int AUDIO_OUTPUT_RETRIEVAL = 1; // no audio, phonemes only
constexpr int ESPEAK_CHARS_UTF8 = 1;
constexpr int PHONEME_MODE_IPA = 2;       // IPA phonemes with stress markers
} // namespace

extern "C" {

/*
 * espeak_Initialize() appends "/espeak-ng-data" to the path it receives, so
 * dataPath must be the PARENT of the espeak-ng-data directory (filesDir).
 */
JNIEXPORT jboolean JNICALL
Java_com_example_tts_kitten_EspeakBridge_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring dataPath) {
    if (dataPath == nullptr) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(dataPath, nullptr);
    if (path == nullptr) return JNI_FALSE;
    int sampleRate = espeak_Initialize(AUDIO_OUTPUT_RETRIEVAL, 0, path, 0);
    env->ReleaseStringUTFChars(dataPath, path);
    if (sampleRate < 0) {
        LOGE("espeak_Initialize failed: %d", sampleRate);
        return JNI_FALSE;
    }
    LOGI("eSpeak-NG ready (sample rate %d)", sampleRate);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_example_tts_kitten_EspeakBridge_nativePhonemize(
        JNIEnv *env, jobject /*thiz*/, jstring text, jstring voice) {
    if (text == nullptr) return env->NewStringUTF("");
    const char *textChars = env->GetStringUTFChars(text, nullptr);
    const char *voiceChars = voice == nullptr ? nullptr : env->GetStringUTFChars(voice, nullptr);
    if (textChars == nullptr) {
        if (voiceChars != nullptr) env->ReleaseStringUTFChars(voice, voiceChars);
        return env->NewStringUTF("");
    }

    if (voiceChars != nullptr && voiceChars[0] != '\0') {
        int voiceResult = espeak_SetVoiceByName(voiceChars);
        if (voiceResult != 0) {
            LOGE("espeak voice '%s' unavailable (%d), using en", voiceChars, voiceResult);
            espeak_SetVoiceByName("en");
        }
    }

    std::string result;
    const char *ptext = textChars;
    while (ptext != nullptr && *ptext != '\0') {
        const char *phonemes = espeak_TextToPhonemes(
                (const void **) &ptext, ESPEAK_CHARS_UTF8, PHONEME_MODE_IPA);
        if (phonemes != nullptr) result += phonemes;
        if (ptext == nullptr) break;
    }

    if (voiceChars != nullptr) env->ReleaseStringUTFChars(voice, voiceChars);
    env->ReleaseStringUTFChars(text, textChars);
    return env->NewStringUTF(result.c_str());
}

} // extern "C"

#else // !KITTEN_TTS_ESPEAK — ABI without the prebuilt eSpeak-NG library

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_tts_kitten_EspeakBridge_nativeInit(
        JNIEnv * /*env*/, jobject /*thiz*/, jstring /*dataPath*/) {
    LOGE("eSpeak-NG is not available for this ABI");
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_tts_kitten_EspeakBridge_nativePhonemize(
        JNIEnv *env, jobject /*thiz*/, jstring /*text*/, jstring /*voice*/) {
    return env->NewStringUTF("");
}

} // extern "C"

#endif
