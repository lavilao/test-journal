package com.example.tts.kitten

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * ONNX Runtime wrapper for one pico language graph.
 *
 * Contract (docs/kitten-tts-2-tiny.md):
 *   input_ids int64 [1, T], style float32 [1, 256], speed float32 [1]
 *   -> waveform float32 [N], durations int64 [T]
 */
class KittenTtsSession(modelPath: String) : AutoCloseable {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    private val inputNames: List<String>
    private val outputNames: List<String>
    private val waveformIndex: Int
    private val durationsIndex: Int

    init {
        val options = OrtSession.SessionOptions().apply {
            // One or two threads is the sweet spot for the 7M-parameter graph.
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            setIntraOpNumThreads(cores.coerceAtMost(2))
            setInterOpNumThreads(1)
        }
        session = environment.createSession(modelPath, options)
        inputNames = session.inputNames.toList()
        outputNames = session.outputNames.toList()
        waveformIndex = outputNames.indexOfFirst { it.contains("wav", ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0
        durationsIndex = if (outputNames.size > 1) {
            outputNames.indexOfFirst { it.contains("dur", ignoreCase = true) }
                .takeIf { it >= 0 && it != waveformIndex }
                ?: (if (waveformIndex == 0) 1 else 0)
        } else {
            -1
        }
    }

    fun run(tokens: LongArray, style: FloatArray, speed: Float): Pair<FloatArray, LongArray>? {
        if (outputNames.isEmpty()) return null
        val inputIds = OnnxTensor.createTensor(
            environment, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())
        )
        val styleTensor = OnnxTensor.createTensor(
            environment, FloatBuffer.wrap(style), longArrayOf(1, style.size.toLong())
        )
        val speedTensor = OnnxTensor.createTensor(
            environment, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1)
        )

        return try {
            val inputs = HashMap<String, OnnxTensor>(3)
            inputIds.use { ids ->
                styleTensor.use { sty ->
                    speedTensor.use { spd ->
                        for ((index, name) in inputNames.withIndex()) {
                            when {
                                name.contains("input_id", ignoreCase = true) ||
                                    name.contains("tokens", ignoreCase = true) -> inputs[name] = ids
                                name.contains("style", ignoreCase = true) -> inputs[name] = sty
                                name.contains("speed", ignoreCase = true) -> inputs[name] = spd
                                index == 0 -> inputs[name] = ids
                                index == 1 -> inputs[name] = sty
                                index == 2 -> inputs[name] = spd
                            }
                        }
                        session.run(inputs).use { result ->
                            val waveformTensor = result.get(waveformIndex) as? OnnxTensor
                                ?: return null
                            val durationsTensor = if (durationsIndex in 0 until outputNames.size) {
                                result.get(durationsIndex) as? OnnxTensor
                            } else {
                                null
                            }
                            val waveform = waveformTensor.floatBuffer.toFloatArray()
                            val durations = durationsTensor?.longBuffer?.toLongArray() ?: LongArray(0)
                            if (waveform.isEmpty()) null else waveform to durations
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun FloatBuffer.toFloatArray(): FloatArray {
        val copy = duplicate()
        val out = FloatArray(copy.remaining())
        copy.get(out)
        return out
    }

    private fun LongBuffer.toLongArray(): LongArray {
        val copy = duplicate()
        val out = LongArray(copy.remaining())
        copy.get(out)
        return out
    }

    override fun close() {
        try {
            session.close()
        } catch (_: Throwable) {
        }
    }
}
