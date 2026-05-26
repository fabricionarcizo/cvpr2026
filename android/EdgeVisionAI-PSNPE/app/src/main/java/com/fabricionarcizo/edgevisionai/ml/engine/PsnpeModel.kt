/*
 * MIT License
 *
 * Copyright (c) 2026 Elizabete Munzlinger and Fabricio Batista Narcizo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.fabricionarcizo.edgevisionai.ml.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap
import com.qualcomm.qti.psnpe.PSNPEManager
import com.fabricionarcizo.edgevisionai.ml.api.InferenceEngine
import com.fabricionarcizo.edgevisionai.ml.api.TensorOutputs
import com.fabricionarcizo.edgevisionai.ml.config.ModelConfig
import com.fabricionarcizo.edgevisionai.ml.preprocess.BitmapRgbFloatPreprocessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/**
 * A class for managing optimized models in the DLC format using PSNPE parallel-run Java APIs.
 *
 * This class initializes and handles a Qualcomm PSNPE (Parallel SNPE) network, copies the model
 * from assets to internal storage, writes a JSON configuration file, and uses the static
 * [PSNPEManager] to load, execute, and release the model.
 *
 * @param application The application context used for accessing assets and other system resources.
 * @param config The configuration details for the model, including file name, input/output layer
 *      names, and input dimensions.
 */
class PsnpeModel(
    private val application: Application,
    private val config: ModelConfig,
) : Closeable,
    InferenceEngine<TensorOutputs> {
    /**
     * A set of private constants used in this class.
     */
    companion object {
        private val TAG = PsnpeModel::class.qualifiedName

        /**
         * Name of the PSNPE JSON configuration file written to internal storage.
         */
        private const val MODEL_CONFIGS_FILE = "model_configs.json"

        /**
         * Placeholder in the `model_configs.json` asset template that is replaced at runtime with
         * the absolute path of the DLC model file on internal storage.
         */
        private const val MODEL_FILE_PLACEHOLDER = "{{MODEL_FILE}}"

        /**
         * Maximum number of initialization retry attempts.
         */
        private const val MAX_RETRIES = 5

        /**
         * Initial delay in milliseconds before first retry.
         */
        private const val INITIAL_DELAY_MS = 500L

        /**
         * Maximum delay in milliseconds between retries.
         */
        private const val MAX_DELAY_MS = 5000L

        /**
         * Number of color channels in RGB images.
         */
        private const val CHANNELS_RGB = 3

        /**
         * Neutral gray value matching the YOLOX letterbox pad value (0–255 scale).
         */
        private const val NEUTRAL_GRAY_VALUE = 114f

        /**
         * Letterbox padding color (matches YOLOX convention: constant 114 gray).
         */
        private const val LETTERBOX_PAD = 114

        /**
         * Index used for single-image synchronous inference (BulkSize = 1).
         */
        private const val INFERENCE_INDEX = 0
    }

    /**
     * Utility object for bitmap-to-buffer preprocessing operations.
     */
    private val bitmapRgbFloatPreprocessor = BitmapRgbFloatPreprocessor()

    /**
     * Input width dimension.
     */
    private val inputW = config.inputNCHW[3]

    /**
     * Input height dimension.
     */
    private val inputH = config.inputNCHW[2]

    /**
     * Paint object with bitmap filtering enabled.
     */
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Reusable bitmap for input preprocessing.
     */
    private var reusableInputBitmap: Bitmap? = null

    /**
     * Reusable canvas for input preprocessing.
     */
    private var reusableCanvas: Canvas? = null

    /**
     * Flag indicating whether the model has been closed.
     */
    @Volatile
    private var isClosed: Boolean = false

    /**
     * Flag indicating whether the model has been initialized.
     */
    @Volatile
    private var isInitialized: Boolean = false

    /**
     * Mutex for thread-safe initialization.
     */
    private val initMutex = Mutex()

    /**
     * Lock object for synchronizing state changes and PSNPEManager access.
     */
    private val stateLock = Any()

    /**
     * The model name derived from the config file name (without extension), used as the key for
     * [PSNPEManager.buildFromFile].
     */
    private val modelName: String = config.fileName.substringBeforeLast(".")

    /**
     * Initializes the model by copying it from assets to internal storage, writing the PSNPE
     * JSON configuration, and building the PSNPE network. This method should be called before
     * using the model for inference.
     *
     * It is safe to call this method multiple times. Subsequent calls will be ignored if the
     * model is already successfully initialized or if it has been closed.
     *
     * Implements exponential backoff retry strategy to handle transient failures during model
     * loading (e.g., DSP initialization timing issues).
     *
     * @throws IllegalStateException if model loading fails after all retry attempts.
     */
    override suspend fun initialize() {
        if (isInitialized || isClosed) return

        initMutex.withLock {
            var successfullyLoaded = isInitialized || isClosed

            if (!successfullyLoaded) {
                var lastException: Exception? = null
                var currentDelay = INITIAL_DELAY_MS

                for (attempt in 1..MAX_RETRIES) {
                    try {
                        Log.d(TAG, "Attempting to load PSNPE model ${config.fileName} ($attempt/$MAX_RETRIES)")

                        if (setupAndBuildModel()) {
                            isInitialized = true
                            successfullyLoaded = true
                            Log.i(TAG, "PSNPE model initialized successfully: ${config.fileName}")
                            break
                        }

                        Log.w(TAG, "PSNPE model loading returned false (attempt $attempt)")
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "PSNPE model loading exception (attempt $attempt): ${e.message}")
                    }

                    if (attempt < MAX_RETRIES && !successfullyLoaded) {
                        delay(currentDelay)
                        currentDelay = (currentDelay * 2).coerceAtMost(MAX_DELAY_MS)
                    }
                }

                if (!successfullyLoaded) {
                    val errorMsg = "Failed to load PSNPE model ${config.fileName} after $MAX_RETRIES attempts"
                    Log.e(TAG, errorMsg, lastException)
                    throw IllegalStateException(errorMsg, lastException)
                }
            }
        }
    }

    /**
     * Copies the DLC model from assets to internal storage, writes the PSNPE JSON configuration
     * file (from the `model_configs.json` asset template), and builds the PSNPE network using
     * [PSNPEManager].
     *
     * @return `true` if the model was successfully set up and built; `false` otherwise.
     */
    private fun setupAndBuildModel(): Boolean {
        val filesDir = application.filesDir

        // Copy the DLC model file from assets to the app's internal storage if not already present.
        val modelFile = File(filesDir, config.fileName)
        if (!modelFile.exists()) {
            Log.d(TAG, "Copying model ${config.fileName} from assets to ${modelFile.absolutePath}")
            application.assets.open(config.fileName).use { input ->
                FileOutputStream(modelFile).use { output -> input.copyTo(output) }
            }
        }

        // Read the JSON config template from assets, substitute the model file path, and write to
        // internal storage so PSNPEManager can load it by file path.
        val configTemplate = application.assets.open(MODEL_CONFIGS_FILE).bufferedReader().readText()
        val configJson = configTemplate.replace(MODEL_FILE_PLACEHOLDER, modelFile.absolutePath)
        val configFile = File(filesDir, MODEL_CONFIGS_FILE)
        configFile.writeText(configJson)
        Log.d(TAG, "PSNPE config written to ${configFile.absolutePath}")

        // Initialize PSNPEManager with the native library directory and config file path.
        val nativeLibDir = application.applicationInfo.nativeLibraryDir
        Log.d(TAG, "PSNPEManager.init(nativeLibDir=$nativeLibDir, configPath=${configFile.absolutePath})")
        if (!PSNPEManager.init(nativeLibDir, configFile.absolutePath)) {
            Log.w(TAG, "PSNPEManager.init() returned false")
            return false
        }

        // Build the PSNPE network from the model name declared in the configuration file.
        Log.d(TAG, "PSNPEManager.buildFromFile(modelName=$modelName)")
        if (!PSNPEManager.buildFromFile(modelName)) {
            Log.w(TAG, "PSNPEManager.buildFromFile() returned false")
            return false
        }

        return true
    }

    /**
     * Runs the PSNPE model on a preprocessed and letterboxed bitmap.
     *
     * If inference fails, the model is marked as uninitialized so that it can be recovered by
     * calling [initialize] again.
     *
     * @param bitmap The original input image to be processed.
     * @param block A lambda function that consumes the output tensor map.
     *
     * @return The result of the block function, or null if inference fails.
     */
    override fun <R> infer(
        bitmap: Bitmap,
        block: (TensorOutputs) -> R,
    ): R? {
        if (isClosed || !isInitialized) return null

        return try {
            executeInference(bitmap, block)
        } catch (e: Exception) {
            Log.e(TAG, "PSNPE inference execution failed", e)

            // Enter recoverable error state: mark as uninitialized so initialize() can retry.
            synchronized(stateLock) {
                isInitialized = false
            }

            null
        }
    }

    /**
     * Executes the actual PSNPE inference operations synchronously.
     *
     * Preprocesses the bitmap, writes data to [PSNPEManager], executes synchronously, and
     * retrieves the output map for the single inference index.
     *
     * @param bitmap The input bitmap to be processed.
     * @param block A lambda function that processes the output tensor map.
     *
     * @return The result of the block function, or null if validation or inference fails.
     */
    private fun <R> executeInference(
        bitmap: Bitmap,
        block: (TensorOutputs) -> R,
    ): R? {
        val inputBmp = getReusableInput(bitmap)

        val isStateValid =
            !isClosed &&
                isInitialized &&
                inputBmp.width == config.inputNCHW[3] &&
                inputBmp.height == config.inputNCHW[2]

        if (!isStateValid) return null

        bitmapRgbFloatPreprocessor.convertBitmapToBuffer(inputBmp)
        val floats = bitmapRgbFloatPreprocessor.bufferToFloatsRGB()

        if (bitmapRgbFloatPreprocessor.wasLastBufferBlack()) return null

        return synchronized(stateLock) {
            if (isClosed || !isInitialized) return@synchronized null

            if (!PSNPEManager.loadData(floats, INFERENCE_INDEX)) {
                Log.w(TAG, "PSNPEManager.loadData() returned false")
                return@synchronized null
            }

            if (!PSNPEManager.executeSync()) {
                Log.w(TAG, "PSNPEManager.executeSync() returned false")
                return@synchronized null
            }

            val outputMap: TensorOutputs = PSNPEManager.getOutputSync(INFERENCE_INDEX)
                ?: return@synchronized null

            block(outputMap)
        }
    }

    /**
     * Retrieves a reusable input bitmap prepared with YOLOX-style letterbox preprocessing.
     *
     * The source bitmap is scaled to fit within the model's input dimensions while preserving its
     * aspect ratio (ratio = min(inputW/srcW, inputH/srcH)). The scaled image is placed at the
     * top-left corner and the remaining area is filled with constant gray (114, 114, 114).
     *
     * @param src The source bitmap to be letterboxed.
     *
     * @return A bitmap of size [inputW × inputH] with the source image letterboxed inside.
     */
    private fun getReusableInput(src: Bitmap): Bitmap {
        val bmp =
            reusableInputBitmap ?: createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888)
                .also {
                    reusableInputBitmap = it
                    reusableCanvas = Canvas(it)
                }

        val ratio = minOf(inputW.toFloat() / src.width, inputH.toFloat() / src.height)
        val scaledW = (src.width * ratio).toInt()
        val scaledH = (src.height * ratio).toInt()

        val padColor = android.graphics.Color.rgb(LETTERBOX_PAD, LETTERBOX_PAD, LETTERBOX_PAD)
        bmp.eraseColor(padColor)

        val destRect = Rect(0, 0, scaledW, scaledH)
        reusableCanvas?.drawBitmap(src, null, destRect, paint)
        return bmp
    }

    /**
     * Releases PSNPE resources and clears references to avoid memory leaks.
     *
     * Thread-safe: uses synchronized block to prevent concurrent access during release.
     */
    override fun close() {
        synchronized(stateLock) {
            if (isClosed) return
            isClosed = true
            runCatching { PSNPEManager.release() }
        }

        reusableCanvas = null
        reusableInputBitmap = null
    }
}
