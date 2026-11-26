package com.example.tts.kitten

/**
 * Turns assistant replies into chunks the pico model can speak.
 *
 * The model is unreliable past ~350 phonemes and drifts into repetition on
 * long input, so text is cut at sentence ends and long sentences at commas,
 * mirroring KittenML's `front_end` / `_wrap`.
 */
object KittenTtsText {

    private const val MAX_CHARS = 320

    private val SENTENCE_SPLIT = Regex("(?<=[.!?…])\\s+")

    fun chunks(text: String): List<String> {
        val cleaned = clean(text)
        if (cleaned.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        for (sentence in SENTENCE_SPLIT.split(cleaned)) {
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.length <= MAX_CHARS) {
                chunks.add(trimmed)
                continue
            }
            // Split over-long sentences at commas first, then at spaces.
            var rest = trimmed
            while (rest.length > MAX_CHARS) {
                var cut = rest.lastIndexOf(',', MAX_CHARS)
                if (cut < MAX_CHARS / 2) cut = rest.lastIndexOf(' ', MAX_CHARS)
                if (cut <= 0) cut = MAX_CHARS
                chunks.add(rest.substring(0, cut).trim())
                rest = rest.substring(cut).trimStart(',', ' ')
            }
            if (rest.isNotEmpty()) chunks.add(rest)
        }
        return chunks
    }

    /** Removes markdown noise the phonemizer would otherwise try to read. */
    fun clean(text: String): String {
        var out = text
        out = out.replace("**", "").replace("__", "").replace("`", "")
        out = out.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        out = out.replace("•", ", ").replace("·", ", ")
        out = out.replace(Regex("\\s+"), " ")
        return out.trim()
    }
}
