package com.example.tts.kitten

/** One built-in KittenTTS 2 pico voice. */
data class KittenVoice(
    val key: String,
    val name: String,
    val description: String
)

/** One pico language model, with the eSpeak voice used for its G2P. */
data class KittenLanguage(
    val code: String,
    val label: String,
    val espeakVoice: String
)

/**
 * Static catalogue of the kitten-tts-2-pico release, mirroring its config.json:
 *
 *  - eight per-language ONNX graphs (en de es it pt ru hi fr),
 *  - four voices per language (two for Hindi),
 *  - a voice always belongs to exactly one language, so picking a voice also
 *    picks its language (KittenML's rule: a voice name decides the language,
 *    and a language without a voice uses its first voice).
 */
object KittenTtsCatalog {

    const val DISPLAY_NAME = "KittenTTS 2 · pico"
    const val REPO_ID = "KittenML/kitten-tts-2-pico"
    const val REPO_BASE = "https://huggingface.co/$REPO_ID/resolve/main"

    const val SAMPLE_RATE = 24_000

    /** Start/end token ids of the pico vocabulary (vocab.json). */
    const val START_TOKEN = 0
    const val END_TOKEN = 10
    const val STYLE_DIM = 256

    val languages: List<KittenLanguage> = listOf(
        KittenLanguage("es", "Español", "es"),
        KittenLanguage("en", "English", "en-us"),
        KittenLanguage("de", "Deutsch", "de"),
        KittenLanguage("fr", "Français", "fr-fr"),
        KittenLanguage("it", "Italiano", "it"),
        KittenLanguage("pt", "Português (Brasil)", "pt-br"),
        KittenLanguage("ru", "Русский", "ru"),
        KittenLanguage("hi", "हिन्दी", "hi")
    )

    private val voicesByLanguage: Map<String, List<KittenVoice>> = mapOf(
        "en" to listOf(
            KittenVoice("eleanor", "Eleanor", "Historiadora sombría (femenina)"),
            KittenVoice("edith", "Edith", "Abuela mayor y suave (femenina)"),
            KittenVoice("otis", "Otis", "Músico de blues grave (masculina)"),
            KittenVoice("frank", "Frank", "Veterano áspero (masculina)")
        ),
        "de" to listOf(
            KittenVoice("anna", "Anna", "Femenina 1"),
            KittenVoice("mia", "Mia", "Femenina 2"),
            KittenVoice("lukas", "Lukas", "Masculina 1"),
            KittenVoice("paul", "Paul", "Masculina 2")
        ),
        "es" to listOf(
            KittenVoice("sofia", "Sofía", "Femenina 1"),
            KittenVoice("elena", "Elena", "Femenina 2"),
            KittenVoice("pablo", "Pablo", "Masculina 1"),
            KittenVoice("mateo", "Mateo", "Masculina 2")
        ),
        "it" to listOf(
            KittenVoice("francesca", "Francesca", "Femenina 1"),
            KittenVoice("alessandro", "Alessandro", "Masculina 1"),
            KittenVoice("giorgio", "Giorgio", "Masculina 2"),
            KittenVoice("davide", "Davide", "Masculina 3")
        ),
        "pt" to listOf(
            KittenVoice("juliana", "Juliana", "Femenina 1"),
            KittenVoice("larissa", "Larissa", "Femenina 2"),
            KittenVoice("gustavo", "Gustavo", "Masculina 1"),
            KittenVoice("caio", "Caio", "Masculina 2")
        ),
        "ru" to listOf(
            KittenVoice("olga", "Olga", "Femenina 1"),
            KittenVoice("tatiana", "Tatiana", "Femenina 2"),
            KittenVoice("sergei", "Sergei", "Masculina 1"),
            KittenVoice("alexei", "Alexei", "Masculina 2")
        ),
        "hi" to listOf(
            KittenVoice("priya", "Priya", "Femenina 1"),
            KittenVoice("arjun", "Arjun", "Masculina 1")
        ),
        "fr" to listOf(
            KittenVoice("amelie", "Amélie", "Femenina 1"),
            KittenVoice("lea", "Léa", "Femenina 2"),
            KittenVoice("theo", "Théo", "Masculina 1"),
            KittenVoice("baptiste", "Baptiste", "Masculina 2")
        )
    )

    /** Default language: the app's assistant answers in Spanish. */
    const val DEFAULT_LANGUAGE = "es"

    fun language(code: String): KittenLanguage =
        languages.firstOrNull { it.code == code } ?: languages.first { it.code == DEFAULT_LANGUAGE }

    fun isSupportedLanguage(code: String): Boolean = languages.any { it.code == code }

    fun voices(language: String): List<KittenVoice> =
        voicesByLanguage[language] ?: voicesByLanguage[DEFAULT_LANGUAGE].orEmpty()

    fun voice(language: String, key: String): KittenVoice {
        val list = voices(language)
        return list.firstOrNull { it.key == key } ?: list.first()
    }

    /** Voice key -> language, or null when the voice is not in the catalogue. */
    fun languageOfVoice(voiceKey: String): String? =
        voicesByLanguage.entries.firstOrNull { entry ->
            entry.value.any { it.key == voiceKey }
        }?.key

    fun modelFile(language: String): String = "kitten_tts_2_pico_${language}.onnx"

    fun voicesFile(language: String): String = "voices_${language}.npz"

    fun downloadUrls(language: String): List<String> = listOf(
        "$REPO_BASE/config.json",
        "$REPO_BASE/vocab.json",
        "$REPO_BASE/${modelFile(language)}",
        "$REPO_BASE/${voicesFile(language)}"
    )
}
