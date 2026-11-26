package com.example.tts.kitten

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device KittenTTS 2 pico engine.
 *
 * Pipeline, identical to KittenML's `KittenTTS2Tiny` ONNX path:
 *   text -> eSpeak-NG IPA -> vocab ids -> [0] + ids + [10, 0]
 *        -> style row = voicePack[min(tokenCount - 1, 509)]
 *        -> ONNX (input_ids, style, speed) -> waveform + durations
 *        -> trim first/last duration frames, 5 ms fades, 0.15 s gap.
 *
 * Audio is written to an AudioTrack chunk by chunk, so playback starts before
 * the whole reply has been synthesized.
 */
object KittenTtsEngine {

    private const val GAP_SECONDS = 0.15f

    private val mutex = Mutex()

    private var session: KittenTtsSession? = null
    private var loadedLanguage: String? = null
    private var vocabulary: KittenTtsVocabulary? = null
    private var voicePack: Map<String, KittenTtsNpz.NpyArray> = emptyMap()

    @Volatile
    private var stopRequested = false

    @Volatile
    private var speaking = false

    private var track: AudioTrack? = null

    /** Synthesizes the selected language/voice and plays it. */
    suspend fun speakSelected(context: Context, text: String): Boolean {
        val language = KittenTtsModelStore.language(context)
        val voice = KittenTtsModelStore.voice(context)
        val speed = KittenTtsModelStore.speed(context)
        return speak(context, text, language, voice, speed)
    }

    suspend fun speak(
        context: Context,
        text: String,
        language: String,
        voiceKey: String,
        speed: Float = 1.0f
    ): Boolean = mutex.withLock {
        if (text.isBlank()) return@withLock false
        val lang = KittenTtsCatalog.language(language)
        if (!KittenTtsModelStore.isDownloaded(context, lang.code)) return@withLock false
        if (!load(context, lang.code)) return@withLock false

        val chunks = KittenTtsText.chunks(text)
        if (chunks.isEmpty()) return@withLock false

        stopRequested = false
        speaking = true
        val audioTrack = openTrack() ?: run {
            speaking = false
            return@withLock false
        }
        val gap = FloatArray((KittenTtsCatalog.SAMPLE_RATE * GAP_SECONDS).toInt())
        var framesWritten = 0L
        var produced = false

        try {
            audioTrack.play()
            for ((index, chunk) in chunks.withIndex()) {
                if (stopRequested) return@withLock false
                val audio = withContext(Dispatchers.Default) {
                    synthesize(chunk, lang, voiceKey, speed, keepStart = index == 0)
                } ?: continue
                if (audio.isEmpty()) continue
                produced = true
                framesWritten += audioTrack.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
                if (index != chunks.lastIndex) {
                    framesWritten += audioTrack.write(gap, 0, gap.size, AudioTrack.WRITE_BLOCKING)
                }
            }
            if (produced) {
                awaitPlayback(audioTrack, framesWritten)
            }
            produced
        } catch (_: Throwable) {
            false
        } finally {
            speaking = false
            try {
                audioTrack.stop()
            } catch (_: Throwable) {
            }
            try {
                audioTrack.release()
            } catch (_: Throwable) {
            }
            track = null
        }
    }

    /** Stops playback at the next chunk boundary. */
    fun stop() {
        stopRequested = true
        try {
            track?.pause()
            track?.flush()
        } catch (_: Throwable) {
        }
    }

    fun release() {
        stop()
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
        loadedLanguage = null
        vocabulary = null
        voicePack = emptyMap()
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private suspend fun load(context: Context, language: String): Boolean {
        if (session != null && loadedLanguage == language && vocabulary != null) return true

        if (!KittenTtsModelStore.ensureEspeakData(context)) return false
        if (!EspeakBridge.init(KittenTtsModelStore.espeakParentDir(context).absolutePath)) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val configFile = KittenTtsModelStore.configFile(context)
                val vocabFile = KittenTtsModelStore.vocabFile(context)
                val modelFile = KittenTtsModelStore.modelFile(context, language)
                val voicesFile = KittenTtsModelStore.voicesFile(context, language)
                if (!configFile.exists() || !vocabFile.exists() ||
                    !modelFile.exists() || !voicesFile.exists()
                ) {
                    return@withContext false
                }

                if (vocabulary == null) {
                    vocabulary = KittenTtsVocabulary.fromJson(vocabFile.readText())
                }
                if (session == null || loadedLanguage != language) {
                    session?.close()
                    session = KittenTtsSession(modelFile.absolutePath)
                    loadedLanguage = language
                    voicePack = voicesFile.inputStream().use { KittenTtsNpz.parseNpz(it) }
                }
                vocabulary != null && voicePack.isNotEmpty()
            } catch (_: Throwable) {
                session?.close()
                session = null
                loadedLanguage = null
                false
            }
        }
    }

    // ------------------------------------------------------------------
    // Synthesis
    // ------------------------------------------------------------------

    private fun synthesize(
        text: String,
        language: KittenLanguage,
        voiceKey: String,
        speed: Float,
        keepStart: Boolean
    ): FloatArray? {
        val ipa = EspeakBridge.phonemize(text, language.espeakVoice)
        val phonemes = KittenTtsPhonemes.normalize(ipa)
        val vocab = vocabulary ?: return null
        val tokens = vocab.tokens(phonemes) ?: return null

        val pack = voicePack[voiceKey]
            ?: voicePack.entries.firstOrNull { it.key.equals(voiceKey, ignoreCase = true) }?.value
            ?: voicePack.values.firstOrNull()
            ?: return null
        val rows = pack.shape.getOrNull(0) ?: return null
        val dim = pack.shape.getOrNull(1) ?: KittenTtsCatalog.STYLE_DIM
        if (rows <= 0 || dim <= 0) return null

        val row = min(tokens.size - 1, rows - 1).coerceAtLeast(0)
        val style = pack.data.copyOfRange(row * dim, (row + 1) * dim)
        val (waveform, durations) = session?.run(tokens, style, speed) ?: return null
        return postProcess(waveform, durations, keepStart)
    }

    private fun postProcess(waveform: FloatArray, durations: LongArray, keepStart: Boolean): FloatArray {
        for (i in waveform.indices) waveform[i] = waveform[i].coerceIn(-1f, 1f)

        val totalFrames = durations.sum().coerceAtLeast(1L)
        val samplesPerFrame = max(
            1,
            (waveform.size.toDouble() / totalFrames.toDouble()).roundToInt()
        )
        val lead = if (keepStart) {
            0
        } else {
            max(0L, (durations.firstOrNull() ?: 1L) - 1L).toInt() * samplesPerFrame
        }
        val trail = max(0L, (durations.lastOrNull() ?: 1L) - 1L).toInt() * samplesPerFrame

        var out = waveform
        if (lead + trail + samplesPerFrame < waveform.size) {
            out = waveform.copyOfRange(lead, if (trail > 0) waveform.size - trail else waveform.size)
        }

        val fade = min(120, out.size / 8)
        if (fade > 0) {
            for (i in 0 until fade) out[i] *= i / fade.toFloat()
            for (i in 0 until fade) out[out.size - 1 - i] *= i / fade.toFloat()
        }
        return out
    }

    // ------------------------------------------------------------------
    // Audio output
    // ------------------------------------------------------------------

    private fun openTrack(): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            KittenTtsCatalog.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferBytes = max(minBuffer * 2, KittenTtsCatalog.SAMPLE_RATE * 4)
        return try {
            val created = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(KittenTtsCatalog.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = created
            created
        } catch (_: Throwable) {
            null
        }
    }

    /** Waits (bounded) for the queued audio to drain after the last write. */
    private fun awaitPlayback(audioTrack: AudioTrack, framesWritten: Long) {
        if (framesWritten <= 0) return
        val deadline = System.currentTimeMillis() + 120_000
        while (!stopRequested && System.currentTimeMillis() < deadline) {
            val head = try {
                audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            } catch (_: Throwable) {
                return
            }
            if (head >= framesWritten) return
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

}
