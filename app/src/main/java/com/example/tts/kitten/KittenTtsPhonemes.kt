package com.example.tts.kitten

import org.json.JSONObject

/**
 * Phoneme post-processing and tokenization for the pico vocabulary.
 *
 * Mirrors KittenML's `phonemize_v1` + `misaki.espeak.EspeakG2P` post-processing:
 *  - ties become `^` and the affricate/diphthong sequences are folded onto
 *    their single IPA symbols (ʧ, ʤ, ʦ, I, W, A, O, Q, S, Y),
 *  - a few marks the 178-symbol vocabulary lacks are dropped or folded,
 *  - `(` and `)` are read as `,` (the Python `_tokens` rule),
 *  - unknown symbols are skipped instead of producing garbage ids.
 */
object KittenTtsPhonemes {

    private const val TIE_BAR = '\u0361'
    private const val COMBINING_TIE = '\u035C'

    /** Longest sequences first, as in EspeakG2P.e2m. */
    private val E2M: List<Pair<String, String>> = listOf(
        "a^ɪ" to "I", "a^ʊ" to "W",
        "d^z" to "ʣ", "d^ʒ" to "ʤ",
        "e^ɪ" to "A",
        "o^ʊ" to "O", "ə^ʊ" to "Q",
        "s^s" to "S",
        "t^s" to "ʦ", "t^ʃ" to "ʧ",
        "ɔ^ɪ" to "Y"
    ).sortedByDescending { it.first.length }

    /** phonemize_v1.FOLD — symbols eSpeak emits that the vocabulary has no row for. */
    private val FOLD: Map<Char, String> = mapOf(
        '\u032A' to "",  // dental mark
        '\u0329' to "",  // syllabic
        '\u030D' to "",  // vertical line above
        '\u032F' to "",  // non-syllabic
        '\u026D' to "l", // retroflex l
        '\u026B' to "l", // velarised l
        '\u0275' to "ø",
        '\u03B5' to "ɛ", // Greek epsilon from eSpeak
        '\u0005' to "", '\u0006' to "", '\u0007' to "", '\u0008' to "",
        '{' to "", '}' to "", '[' to "", ']' to "",
        '¿' to "", '¡' to "",
        '\u202F' to " "
    )

    fun normalize(ipa: String): String {
        if (ipa.isEmpty()) return ipa
        // eSpeak/phonemizer ties -> '^' so the affricate table applies.
        var text = ipa.replace(TIE_BAR, '^').replace(COMBINING_TIE, '^')
        for ((from, to) in E2M) {
            if (text.contains(from)) text = text.replace(from, to)
        }
        text = text.replace("^", "")
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        for (ch in text) {
            val folded = FOLD[ch]
            if (folded != null) out.append(folded) else out.append(ch)
        }
        return out.toString().trim()
    }
}

/** vocab.json -> token ids, plus the pico start/end framing. */
class KittenTtsVocabulary private constructor(private val ids: Map<Char, Int>) {

    /**
     * `[0] + phoneme ids + [10, 0]` (KittenML `_tokens`). Returns null when the
     * chunk has no pronounceable phoneme.
     */
    fun tokens(phonemes: String): LongArray? {
        val out = ArrayList<Long>(phonemes.length + 3)
        out.add(KittenTtsCatalog.START_TOKEN.toLong())
        var spoken = 0
        for (ch in phonemes) {
            val id = ids[ch] ?: if (ch == '(' || ch == ')') ids[','] else null
            if (id != null) {
                out.add(id.toLong())
                spoken++
            }
        }
        if (spoken == 0) return null
        out.add(KittenTtsCatalog.END_TOKEN.toLong())
        out.add(KittenTtsCatalog.START_TOKEN.toLong())
        return out.toLongArray()
    }

    companion object {
        fun fromJson(json: String): KittenTtsVocabulary {
            val obj = JSONObject(json)
            val map = HashMap<Char, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.length == 1) map[key[0]] = obj.optInt(key, -1).takeIf { it >= 0 } ?: continue
            }
            return KittenTtsVocabulary(map)
        }
    }
}
