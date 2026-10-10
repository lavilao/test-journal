package com.example.ai.needle

import org.json.JSONObject

/**
 * Parsed result of NeedleRuntime.transcribe() — Whistle's raw JSON is
 * {"text":"...","language":"es","ttft_ms":171.2,"decode_tps":29.7}.
 *
 * Before this existed, three call sites showed the RAW JSON string to the
 * user (the "Gad Damit." bug report); now they all share this parser.
 */
data class WhistleResult(
    val text: String,
    val language: String?,
    val ttftMs: Double?,
    val decodeTps: Double?
) {
    /** Honest one-line telemetry for diagnostics UIs. */
    fun statsLine(): String? {
        val parts = mutableListOf<String>()
        language?.let { parts.add("idioma $it") }
        ttftMs?.let { parts.add("primera palabra ${"%.0f".format(it)} ms") }
        decodeTps?.let { parts.add("${"%.1f".format(it)} palabras/s") }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    companion object {
        /**
         * Parses the raw JSON payload; falls back to treating the payload as
         * plain text so a future engine format change never leaks JSON to the
         * user again.
         */
        fun parse(raw: String?): WhistleResult? {
            if (raw.isNullOrBlank()) return null
            return try {
                val root = JSONObject(raw)
                val text = root.optString("text", "").trim()
                WhistleResult(
                    text = text,
                    language = root.optString("language").takeIf { it.isNotBlank() },
                    ttftMs = root.optDouble("ttft_ms", Double.NaN).takeIf { !it.isNaN() },
                    decodeTps = root.optDouble("decode_tps", Double.NaN).takeIf { !it.isNaN() }
                )
            } catch (_: Exception) {
                WhistleResult(text = raw.trim(), language = null, ttftMs = null, decodeTps = null)
            }
        }
    }
}
