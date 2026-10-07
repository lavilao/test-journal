package com.example.speech.voiceprint

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device speaker verification ("Voice Match" propio).
 *
 * Google's Voice Match models (the enrolled "Hey Google" voice model) are
 * privileged and NOT accessible to third-party apps, so this engine builds
 * our own, smaller voice print: 12 MFCC coefficients per voiced frame,
 * aggregated into a mean+std "voice embedding". Verification is cosine
 * similarity against the enrolled centroid.
 *
 * Is this as strong as Google's neural model? No — and the UI says so. But
 * it is a REAL on-device verification with no network and no fake data: the
 * same voice scores high, a different voice/room scores lower.
 */
object VoicePrintEngine {

    const val SAMPLE_RATE = 16000
    const val FRAME_SIZE = 400          // 25 ms
    const val FRAME_SHIFT = 160         // 10 ms
    const val FFT_SIZE = 512
    const val NUM_MEL_FILTERS = 26
    const val NUM_MFCC = 12             // c1..c12 (c0 dropped: energy only)
    const val EMBEDDING_DIM = 2 * NUM_MFCC

    /** Below this many voiced frames the sample is too short to verify. */
    private const val MIN_VOICED_FRAMES = 12

    // --- Mel filterbank (built once) ---
    private val melFilters: Array<FloatArray> by lazy { buildMelFilterbank() }

    private fun hzToMel(hz: Double): Double = 2595.0 * kotlin.math.ln(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (kotlin.math.exp(mel / 2595.0) - 1.0)

    private fun buildMelFilterbank(): Array<FloatArray> {
        val nyquist = SAMPLE_RATE / 2.0
        val lowMel = hzToMel(60.0)
        val highMel = hzToMel(nyquist)
        val points = NUM_MEL_FILTERS + 2
        val melPoints = DoubleArray(points) { lowMel + (highMel - lowMel) * it / (points - 1) }
        val hzPoints = DoubleArray(points) { melToHz(melPoints[it]) }
        val bin = DoubleArray(points) { hzPoints[it] * FFT_SIZE / SAMPLE_RATE }

        val filters = Array(NUM_MEL_FILTERS) { FloatArray(FFT_SIZE / 2) }
        for (m in 0 until NUM_MEL_FILTERS) {
            val left = bin[m]
            val center = bin[m + 1]
            val right = bin[m + 2]
            for (k in 0 until FFT_SIZE / 2) {
                val weight = when {
                    k < left || k > right -> 0.0
                    k <= center -> (k - left) / (center - left).coerceAtLeast(1e-10)
                    else -> (right - k) / (right - center).coerceAtLeast(1e-10)
                }
                filters[m][k] = weight.toFloat()
            }
        }
        return filters
    }

    // --- FFT (in-place radix-2) ---
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        // bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + half] * curRe - im[i + k + half] * curIm
                    val vIm = re[i + k + half] * curIm + im[i + k + half] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes the voice-print embedding for a raw PCM16 mono 16 kHz sample.
     * Returns null when the sample is too short or too quiet to be judged.
     */
    fun computeEmbedding(samples: ShortArray): FloatArray? {
        if (samples.size < SAMPLE_RATE) return null // < 1 s

        val numFrames = (samples.size - FRAME_SIZE) / FRAME_SHIFT + 1
        if (numFrames < MIN_VOICED_FRAMES) return null

        val window = FloatArray(FRAME_SIZE) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_SIZE - 1))).toFloat()
        }

        val frameMfcc = Array(numFrames) { FloatArray(NUM_MFCC) }
        val frameEnergy = DoubleArray(numFrames)

        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        var prevSample = 0.0

        for (f in 0 until numFrames) {
            val start = f * FRAME_SHIFT
            // pre-emphasis + Hamming window
            for (i in 0 until FRAME_SIZE) {
                val s = samples[start + i] / 32768.0
                val emph = s - 0.97 * prevSample
                prevSample = s
                re[i] = (emph * window[i]).toFloat()
                im[i] = 0f
            }
            prevSample = samples[start + FRAME_SIZE - 1] / 32768.0
            for (i in FRAME_SIZE until FFT_SIZE) {
                re[i] = 0f
                im[i] = 0f
            }
            fft(re, im)

            // power spectrum
            val power = DoubleArray(FFT_SIZE / 2)
            for (k in 0 until FFT_SIZE / 2) {
                power[k] = (re[k] * re[k] + im[k] * im[k]).toDouble()
            }

            // mel energies -> log
            val logMel = DoubleArray(NUM_MEL_FILTERS)
            for (m in 0 until NUM_MEL_FILTERS) {
                var acc = 0.0
                val filter = melFilters[m]
                for (k in 0 until FFT_SIZE / 2) {
                    acc += power[k] * filter[k]
                }
                logMel[m] = kotlin.math.ln(acc + 1e-10)
            }
            frameEnergy[f] = logMel.sum()

            // DCT-II, coefficients 1..12
            for (k in 1..NUM_MFCC) {
                var acc = 0.0
                for (m in 0 until NUM_MEL_FILTERS) {
                    acc += logMel[m] * cos(PI * k * (m + 0.5) / NUM_MEL_FILTERS)
                }
                frameMfcc[f][k - 1] = acc.toFloat()
            }
        }

        // Keep only voiced frames (above 25% of the max energy) so silence
        // does not dilute the voice print.
        val maxEnergy = frameEnergy.maxOrNull() ?: 0.0
        val voiced = (0 until numFrames).filter { frameEnergy[it] > maxEnergy * 0.25 }
        if (voiced.size < MIN_VOICED_FRAMES) return null

        val embedding = FloatArray(EMBEDDING_DIM)
        for (c in 0 until NUM_MFCC) {
            var mean = 0f
            voiced.forEach { mean += frameMfcc[it][c] }
            mean /= voiced.size
            var variance = 0f
            voiced.forEach { v ->
                val d = frameMfcc[v][c] - mean
                variance += d * d
            }
            val std = sqrt(variance / voiced.size)
            embedding[c] = mean
            embedding[NUM_MFCC + c] = std
        }
        return l2normalize(embedding)
    }

    private fun l2normalize(v: FloatArray): FloatArray {
        var norm = 0f
        v.forEach { norm += it * it }
        norm = sqrt(norm)
        if (norm < 1e-8f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    /** Cosine similarity in [-1, 1]. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    /** Average of several embeddings, normalized (the enrolled centroid). */
    fun average(embeddings: List<FloatArray>): FloatArray? {
        if (embeddings.isEmpty()) return null
        val dim = embeddings[0].size
        val avg = FloatArray(dim)
        embeddings.forEach { e ->
            for (i in 0 until dim) avg[i] += e[i]
        }
        for (i in 0 until dim) avg[i] /= embeddings.size
        return l2normalize(avg)
    }
}

/**
 * Persistent storage for the enrolled voice print.
 */
class VoicePrintStore(context: Context) {

    companion object {
        private const val PREFS = "voice_print_prefs"
        private const val KEY_CENTROID = "centroid_b64"
        private const val KEY_SAMPLES = "sample_count"

        /** Verification thresholds: same voice should clear these. */
        const val THRESHOLD_LOW = 0.75f    // sensible (menos falsos rechazos)
        const val THRESHOLD_MEDIUM = 0.80f // por defecto
        const val THRESHOLD_HIGH = 0.86f   // estricto (menos falsos aceptados)
        const val DEFAULT_THRESHOLD = THRESHOLD_MEDIUM

        fun thresholdLabel(t: Float): String = when (t) {
            THRESHOLD_LOW -> "Sensible"
            THRESHOLD_MEDIUM -> "Media"
            else -> "Estricta"
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnrolled(): Boolean = prefs.contains(KEY_CENTROID)

    fun sampleCount(): Int = prefs.getInt(KEY_SAMPLES, 0)

    fun threshold(): Float = when (prefs.getString("threshold", "medium")) {
        "low" -> THRESHOLD_LOW
        "high" -> THRESHOLD_HIGH
        else -> THRESHOLD_MEDIUM
    }

    fun setThreshold(value: String) {
        prefs.edit().putString("threshold", value).apply()
    }

    /** Re-enrolls from the given sample embeddings. */
    fun enroll(embeddings: List<FloatArray>) {
        val centroid = VoicePrintEngine.average(embeddings) ?: return
        prefs.edit()
            .putString(KEY_CENTROID, encode(centroid))
            .putInt(KEY_SAMPLES, embeddings.size)
            .apply()
    }

    /** Adds one sample to the print (re-centroid of stored + new). */
    fun addSample(embedding: FloatArray): Boolean {
        val current = decode(prefs.getString(KEY_CENTROID, null)) ?: return false
        val count = prefs.getInt(KEY_SAMPLES, 0)
        // weighted average: keep the centroid stable as samples accumulate
        val weight = count.coerceAtLeast(1)
        val blended = FloatArray(current.size) { i ->
            (current[i] * weight + embedding[i]) / (weight + 1)
        }
        var norm = 0f
        blended.forEach { norm += it * it }
        norm = sqrt(norm)
        val normalized = if (norm < 1e-8f) blended else FloatArray(blended.size) { blended[it] / norm }
        prefs.edit()
            .putString(KEY_CENTROID, encode(normalized))
            .putInt(KEY_SAMPLES, count + 1)
            .apply()
        return true
    }

    /** Resets the centroid and starts over with the first sample. */
    fun startEnrollment(embedding: FloatArray) {
        prefs.edit()
            .putString(KEY_CENTROID, encode(embedding))
            .putInt(KEY_SAMPLES, 1)
            .apply()
    }

    /** Verifies an embedding against the enrolled centroid. Null if not enrolled. */
    fun verify(embedding: FloatArray): Float? {
        val centroid = decode(prefs.getString(KEY_CENTROID, null)) ?: return null
        return VoicePrintEngine.cosineSimilarity(centroid, embedding)
    }

    fun verifyVerdict(embedding: FloatArray): Pair<Float, Boolean>? {
        val score = verify(embedding) ?: return null
        return score to (score >= threshold())
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // --- Base64 (float32 little endian) <-> FloatArray ---

    private fun encode(values: FloatArray): String {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    private fun decode(value: String?): FloatArray? {
        if (value.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(bytes.size / 4)
            for (i in out.indices) out[i] = buffer.float
            out
        } catch (_: Exception) {
            null
        }
    }

    /** Honest quality check for a recorded sample (RMS in normalized PCM). */
    fun sampleQuality(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var acc = 0.0
        samples.forEach { acc += (it / 32768.0) * (it / 32768.0) }
        return sqrt(acc / samples.size).toFloat()
    }

    fun isAudible(samples: ShortArray): Boolean {
        var peak = 0
        samples.forEach { s -> if (abs(s.toInt()) > peak) peak = abs(s.toInt()) }
        return peak > 900 // ~3% of full scale
    }
}
