package com.example.speech.hotword

import java.text.Normalizer

/**
 * Spanish-friendly wake-word matching on recognized text.
 *
 * ASR output is noisy ("oye mnemosyne" may come back as "oye mnemosine",
 * "oiga mnemósine"…), so matching is fuzzy: per-token Damerau-free
 * Levenshtein with a tolerance that scales with token length, plus accent
 * and punctuation normalization.
 */
object WakeWordMatcher {

    fun normalize(text: String): String {
        val lowered = text.lowercase().trim()
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        val stripped = decomposed.replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** True when [spoken] contains something close enough to [wake]. */
    fun containsWakeWord(spoken: String, wake: String): Boolean {
        val wakeNorm = normalize(wake)
        if (wakeNorm.isBlank()) return false
        val spokenNorm = normalize(spoken)
        if (spokenNorm.isBlank()) return false

        val wakeTokens = wakeNorm.split(" ")
        val spokenTokens = spokenNorm.split(" ")

        // Whole wake word contained verbatim (fast path).
        if (spokenNorm.contains(wakeNorm)) return true

        if (spokenTokens.size < wakeTokens.size) {
            // Spoken is shorter than the wake word itself: only a fuzzy
            // whole-string comparison makes sense.
            return levenshtein(spokenNorm.replace(" ", ""), wakeNorm.replace(" ", "")) <=
                wakeNorm.length / 3
        }

        for (start in 0..spokenTokens.size - wakeTokens.size) {
            val window = spokenTokens.subList(start, start + wakeTokens.size)
            val all = window.zip(wakeTokens).all { (a, b) -> tokenMatches(a, b) }
            if (all) return true
        }
        return false
    }

    /**
     * Removes the wake word (best matching window) from [spoken], leaving
     * the command text that followed it.
     */
    fun stripWakeWord(spoken: String, wake: String): String {
        val wakeNorm = normalize(wake)
        if (wakeNorm.isBlank()) return normalize(spoken)

        val spokenTokens = normalize(spoken).split(" ").filter { it.isNotBlank() }
        val wakeTokens = wakeNorm.split(" ").filter { it.isNotBlank() }
        if (spokenTokens.isEmpty() || wakeTokens.isEmpty()) return normalize(spoken)

        var bestStart = -1
        var bestCost = Int.MAX_VALUE
        for (start in 0..spokenTokens.size - wakeTokens.size) {
            val window = spokenTokens.subList(start, start + wakeTokens.size)
            var cost = 0
            window.zip(wakeTokens).forEach { (a, b) ->
                if (a != b) cost += levenshtein(a, b)
            }
            if (cost < bestCost) {
                bestCost = cost
                bestStart = start
            }
        }
        if (bestStart < 0) return spokenTokens.joinToString(" ")

        val remaining = spokenTokens.toMutableList()
        repeat(wakeTokens.size) { remaining.removeAt(bestStart) }
        return remaining.joinToString(" ").trim()
    }

    private fun tokenMatches(spokenToken: String, wakeToken: String): Boolean {
        if (spokenToken == wakeToken) return true
        val tolerance = when (wakeToken.length) {
            0, 1, 2 -> 0
            in 3..4 -> if (wakeToken.length >= 4) 1 else 0
            in 5..7 -> 1
            else -> 2
        }
        return levenshtein(spokenToken, wakeToken) <= tolerance
    }

    /** Classic edit distance (Levenshtein). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,        // deletion
                    curr[j - 1] + 1,    // insertion
                    prev[j - 1] + cost  // substitution
                )
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }
}
