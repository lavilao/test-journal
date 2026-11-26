package com.example.tts.kitten

/**
 * JNI entry point to eSpeak-NG 1.52.0 (libttsespeak.so + kitten_tts_jni.cpp).
 *
 * Only the KittenTTS phoneme front end needs this: the model consumes IPA
 * phoneme ids, and eSpeak-NG is the G2P KittenML uses for every language
 * except English.
 */
object EspeakBridge {

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var initialized = false

    @Synchronized
    fun loadLibrary(): Boolean {
        if (libraryLoaded) return true
        return try {
            System.loadLibrary("ttsespeak")
            System.loadLibrary("kittenttsjni")
            libraryLoaded = true
            true
        } catch (t: Throwable) {
            libraryLoaded = false
            false
        }
    }

    /**
     * @param filesDirParent absolute path whose child is `espeak-ng-data`
     *   (eSpeak-NG appends `/espeak-ng-data` itself).
     */
    @Synchronized
    fun init(filesDirParent: String): Boolean {
        if (initialized) return true
        if (!loadLibrary()) return false
        val ok = try {
            nativeInit(filesDirParent)
        } catch (_: Throwable) {
            false
        }
        initialized = ok
        return ok
    }

    fun isReady(): Boolean = initialized

    /** Text -> IPA string for [language] (an eSpeak voice name such as "es"). */
    fun phonemize(text: String, language: String): String {
        if (!initialized || text.isBlank()) return ""
        return try {
            nativePhonemize(text, language)
        } catch (_: Throwable) {
            ""
        }
    }

    private external fun nativeInit(dataPath: String): Boolean
    private external fun nativePhonemize(text: String, language: String): String
}
