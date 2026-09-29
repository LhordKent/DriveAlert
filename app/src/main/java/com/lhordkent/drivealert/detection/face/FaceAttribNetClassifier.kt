package com.lhordkent.drivealert.detection.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.lhordkent.drivealert.detection.model.FaceAttributeProbabilities
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

internal data class FaceAttributeInference(
    val probabilities: FaceAttributeProbabilities,
    val durationMs: Double,
)

internal class FaceAttribNetClassifier(
    context: Context,
    modelAssetPath: String = MODEL_ASSET_PATH,
) : Closeable {
    private val interpreter = Interpreter(
        loadMappedAsset(context, modelAssetPath),
        Interpreter.Options()
            .setNumThreads(2)
            .setUseXNNPACK(true),
    )
    private val input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNEL_COUNT)
        .order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(FaceAttributeTensor.ATTRIBUTE_COUNT)
        .order(ByteOrder.nativeOrder())
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val scaledFace = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(scaledFace)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val inputScale: Float
    private val inputZeroPoint: Int
    private val outputScale: Float
    private val outputZeroPoint: Int

    init {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        require(inputTensor.shape().contentEquals(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, CHANNEL_COUNT))) {
            "Unexpected FaceAttribNet input shape: ${inputTensor.shape().contentToString()}"
        }
        require(inputTensor.dataType() == DataType.UINT8) { "FaceAttribNet input must be UINT8." }
        require(outputTensor.shape().contentEquals(intArrayOf(1, FaceAttributeTensor.ATTRIBUTE_COUNT))) {
            "Unexpected FaceAttribNet output shape: ${outputTensor.shape().contentToString()}"
        }
        require(outputTensor.dataType() == DataType.UINT8) { "FaceAttribNet output must be UINT8." }
        inputScale = inputTensor.quantizationParams().scale
        inputZeroPoint = inputTensor.quantizationParams().zeroPoint
        outputScale = outputTensor.quantizationParams().scale
        outputZeroPoint = outputTensor.quantizationParams().zeroPoint
        require(abs(inputScale - EXPECTED_INPUT_SCALE) < SCALE_TOLERANCE && inputZeroPoint == 0) {
            "Unexpected FaceAttribNet input quantization metadata."
        }
        require(abs(outputScale - EXPECTED_OUTPUT_SCALE) < SCALE_TOLERANCE && outputZeroPoint == 0) {
            "Unexpected FaceAttribNet output quantization metadata."
        }
    }

    fun infer(
        bitmap: Bitmap,
        landmarks: List<NormalizedLandmark>,
        frameWidth: Int,
        frameHeight: Int,
    ): FaceAttributeInference? {
        require(bitmap.width == frameWidth && bitmap.height == frameHeight) {
            "FaceAttribNet frame dimensions do not match the bitmap."
        }
        val crop = FaceCropCalculator.calculate(landmarks, frameWidth, frameHeight) ?: return null
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(
            bitmap,
            Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
            Rect(0, 0, INPUT_SIZE, INPUT_SIZE),
            paint,
        )
        scaledFace.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        input.rewind()
        pixels.forEach { color ->
            input.put(quantizeChannel((color shr 16) and 0xFF))
            input.put(quantizeChannel((color shr 8) and 0xFF))
            input.put(quantizeChannel(color and 0xFF))
        }
        input.rewind()
        output.rewind()
        val startedNs = System.nanoTime()
        interpreter.run(input, output)
        val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
        output.rewind()
        val quantized = ByteArray(FaceAttributeTensor.ATTRIBUTE_COUNT)
        output.get(quantized)
        return FaceAttributeInference(
            probabilities = FaceAttributeTensor.probabilities(quantized, outputScale, outputZeroPoint),
            durationMs = durationMs,
        )
    }

    private fun quantizeChannel(channel: Int): Byte {
        val realValue = channel / 255.0f
        return (realValue / inputScale + inputZeroPoint)
            .roundToInt()
            .coerceIn(0, 255)
            .toByte()
    }

    override fun close() {
        interpreter.close()
        scaledFace.recycle()
    }

    companion object {
        const val MODEL_ASSET_PATH = "face_attrib_net.tflite"
        const val INPUT_SIZE = 128
        private const val CHANNEL_COUNT = 3
        private const val EXPECTED_INPUT_SCALE = 0.0039209677f
        private const val EXPECTED_OUTPUT_SCALE = 0.00390625f
        private const val SCALE_TOLERANCE = 0.0000001f

        private fun loadMappedAsset(context: Context, assetPath: String): ByteBuffer {
            context.assets.openFd(assetPath).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    return channel.map(
                        java.nio.channels.FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
        }
    }
}
