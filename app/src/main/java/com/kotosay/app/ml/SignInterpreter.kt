package com.kotosay.app.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Input:  [1, 30, 780] float32
 * Output: [1, 98] float32 probabilities
 *
 * The FP16 model has FP16 weights but intentionally retains FP32 I/O.
 */
class SignInterpreter(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "SignInterpreter"

        private const val MODEL_FILE =
            "bim_lstm_v312_fp16.tflite"

        private const val NUM_CLASSES = 98
        private const val SEQUENCE_LENGTH = 30
        private const val NUM_FEATURES = 780

        private const val INPUT_FLOAT_COUNT =
            SEQUENCE_LENGTH * NUM_FEATURES

        private const val INPUT_BYTE_COUNT =
            INPUT_FLOAT_COUNT * Float.SIZE_BYTES
    }

    private val interpreter: Interpreter

    // Reused for every inference.
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_BYTE_COUNT).apply {
            order(ByteOrder.nativeOrder())
        }

    private val outputBuffer =
        Array(1) { FloatArray(NUM_CLASSES) }

    init {
        val model = loadModelFile(context)

        // CPU (XNNPACK) only. The GPU delegate cannot take this graph: the
        // BiLSTM has a dynamic-sized tensor, so applying it failed with "Error
        // applying delegate" on every launch, after ~3s of OpenCL setup.
        val cpuOptions = Interpreter.Options().apply {
            setNumThreads(
                Runtime.getRuntime()
                    .availableProcessors()
                    .coerceIn(2, 4)
            )
        }
        interpreter = Interpreter(model, cpuOptions)

        validateTensorShapes()
        warmUp()

        Log.i(
            TAG,
            "Loaded $MODEL_FILE on CPU"
        )
    }

    private fun validateTensorShapes() {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)

        val inputShape = inputTensor.shape()
        val outputShape = outputTensor.shape()

        require(inputShape.contentEquals(intArrayOf(1, 30, 780))) {
            "Unexpected input shape: ${inputShape.contentToString()}"
        }

        require(outputShape.contentEquals(intArrayOf(1, 98))) {
            "Unexpected output shape: ${outputShape.contentToString()}"
        }

        Log.i(
            TAG,
            "Input=${inputShape.contentToString()} " +
                "${inputTensor.dataType()}, " +
                "output=${outputShape.contentToString()} " +
                outputTensor.dataType()
        )
    }

    /**
     * Warm-up keeps the first real prediction from paying XNNPACK's one-off
     * setup cost mid-signing.
     */
    private fun warmUp() {
        val zeros = FloatArray(INPUT_FLOAT_COUNT)

        repeat(3) {
            predictInternal(zeros)
        }
    }

    private fun predictInternal(features: FloatArray): FloatArray {
        require(features.size == INPUT_FLOAT_COUNT) {
            "Expected $INPUT_FLOAT_COUNT values, got ${features.size}"
        }

        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(features)
        inputBuffer.rewind()

        interpreter.run(inputBuffer, outputBuffer)

        // Copy so callers never retain the mutable shared output array.
        return outputBuffer[0].copyOf()
    }

    fun predictTopClass(features: FloatArray): Pair<Int, Float> {
        val probabilities = predictInternal(features)

        var bestIndex = 0
        var bestConfidence = probabilities[0]

        for (index in 1 until NUM_CLASSES) {
            if (probabilities[index] > bestConfidence) {
                bestConfidence = probabilities[index]
                bestIndex = index
            }
        }

        return bestIndex to bestConfidence
    }

    /**
     * Useful for comparing GPU and CPU builds on the real device.
     */
    fun benchmark(
        features: FloatArray,
        iterations: Int = 30
    ): Double {
        repeat(5) {
            predictInternal(features)
        }

        val startNs = SystemClock.elapsedRealtimeNanos()

        repeat(iterations) {
            predictInternal(features)
        }

        return (
            SystemClock.elapsedRealtimeNanos() - startNs
        ) / 1_000_000.0 / iterations
    }

    override fun close() {
        interpreter.close()
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        context.assets.openFd(MODEL_FILE).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                return stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        }
    }
}