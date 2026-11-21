package com.example.speech.hotword

import kotlin.math.sqrt

/**
 * Ultra-cheap time-domain RMS Voice Activity Detection gate for the always-on
 * Whistle hotword loop. Prevents invoking native neural speech recognition on
 * silent room audio, eliminating unnecessary CPU/battery drain.
 */
object WhistleVadGate {
    const val DEFAULT_RMS_THRESHOLD = 220f
    const val DEFAULT_HANGOVER_MS = 2_600L

    fun computeRms(samples: ShortArray, count: Int = samples.size): Float {
        val n = count.coerceAtMost(samples.size)
        if (n <= 0) return 0f
        var sumSq = 0.0
        for (i in 0 until n) {
            val s = samples[i].toDouble()
            sumSq += s * s
        }
        return sqrt(sumSq / n).toFloat()
    }

    fun shouldProcessChunk(
        rms: Float,
        nowMs: Long,
        lastVoiceMs: Long,
        armed: Boolean,
        threshold: Float = DEFAULT_RMS_THRESHOLD,
        hangoverMs: Long = DEFAULT_HANGOVER_MS
    ): Boolean {
        if (armed) return true
        if (rms >= threshold) return true
        return lastVoiceMs > 0L && (nowMs - lastVoiceMs) <= hangoverMs
    }
}
