package com.photoselectortoolbox.domain.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device AI aesthetic scorer.
 *
 * This is the resource-constrained counterpart to the desktop app's aesthetic
 * scoring. Instead of a heavy vision-language model, it uses a small
 * **NIMA (MobileNet)** regressor exported to TensorFlow Lite. The model outputs
 * a probability distribution over the discrete ratings 1..10; the aesthetic
 * score is that distribution's expected value, mapped to a 1.0–10.0 scale.
 *
 * Design notes / constraints:
 * - The `.tflite` model is loaded from `assets/[MODEL_ASSET]`. If the asset is
 *   missing (or TFLite fails to initialise) the analyzer degrades gracefully to
 *   returning `null`, so the app builds and runs without a bundled model and no
 *   aesthetic score is shown until a model is provided.
 * - Intended to run **only on images that pass the cheap OpenCV technical gate**
 *   (see `ScanImagesUseCase`) to conserve battery.
 * - Input/preprocessing assumes a float32 224×224×3 NHWC input normalised with
 *   ImageNet mean/std. TODO(device): adjust [INPUT_SIZE], quantisation, and the
 *   normalisation to match the exact model you ship, then verify on the Galaxy
 *   Tab-Ultra emulator.
 */
@Singleton
class AestheticAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AestheticAnalyzer"

        /** Bundle a NIMA-MobileNet model at this assets path to enable scoring. */
        private const val MODEL_ASSET = "nima_mobilenet.tflite"

        private const val INPUT_SIZE = 224
        private const val NUM_RATINGS = 10

        // ImageNet normalisation (typical for MobileNet-based NIMA exports).
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    @Volatile
    private var interpreter: Interpreter? = null

    @Volatile
    private var initialised = false

    @Volatile
    private var modelAvailable = false

    /**
     * Whether a usable model is present. Lets callers skip work entirely when
     * no model is bundled.
     */
    fun isAvailable(): Boolean {
        ensureInitialised()
        return modelAvailable
    }

    @Synchronized
    private fun ensureInitialised() {
        if (initialised) return
        initialised = true
        try {
            val model = loadModelFile()
            if (model == null) {
                Log.i(TAG, "No aesthetic model asset '$MODEL_ASSET' bundled; scoring disabled.")
                modelAvailable = false
                return
            }
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                // NNAPI/GPU delegates can be added here on capable devices.
            }
            interpreter = Interpreter(model, options)
            modelAvailable = true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to initialise aesthetic model; scoring disabled.", e)
            modelAvailable = false
            interpreter = null
        }
    }

    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            context.assets.openFd(MODEL_ASSET).use { afd ->
                java.io.FileInputStream(afd.fileDescriptor).use { fis ->
                    fis.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        } catch (e: Exception) {
            // Asset not present (or unreadable) — scoring stays disabled.
            null
        }
    }

    /**
     * Score the bitmap's aesthetic quality.
     *
     * @return a score on the 1.0–10.0 scale, or `null` if no model is available
     *         or inference fails.
     */
    fun analyze(bitmap: Bitmap): Double? {
        ensureInitialised()
        val tflite = interpreter ?: return null
        return try {
            val input = preprocess(bitmap)
            val output = Array(1) { FloatArray(NUM_RATINGS) }
            tflite.run(input, output)
            distributionToScore(output[0])
        } catch (e: Throwable) {
            Log.w(TAG, "Aesthetic inference failed", e)
            null
        }
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.putFloat((r - MEAN[0]) / STD[0])
            buffer.putFloat((g - MEAN[1]) / STD[1])
            buffer.putFloat((b - MEAN[2]) / STD[2])
        }
        if (scaled != bitmap) scaled.recycle()
        buffer.rewind()
        return buffer
    }

    /**
     * NIMA outputs a probability distribution over ratings 1..10; the score is
     * the expected value. Visible for testing.
     */
    fun distributionToScore(probs: FloatArray): Double? {
        if (probs.isEmpty()) return null
        var total = 0.0
        var weighted = 0.0
        for (i in probs.indices) {
            total += probs[i]
            weighted += (i + 1) * probs[i]
        }
        if (total <= 0.0) return null
        val mean = weighted / total
        return mean.coerceIn(1.0, 10.0)
    }
}
