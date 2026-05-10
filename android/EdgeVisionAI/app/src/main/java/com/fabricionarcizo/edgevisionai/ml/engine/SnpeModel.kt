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
import com.qualcomm.qti.snpe.FloatTensor
import com.qualcomm.qti.snpe.NeuralNetwork
import com.qualcomm.qti.snpe.SNPE
import com.fabricionarcizo.edgevisionai.ml.api.InferenceEngine
import com.fabricionarcizo.edgevisionai.ml.api.TensorOutputs
import com.fabricionarcizo.edgevisionai.ml.config.ModelConfig
import com.fabricionarcizo.edgevisionai.ml.preprocess.BitmapRgbFloatPreprocessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/**
 * A class for managing optimized models in the DLC format.
 *
 * This class initializes and handles a Qualcomm SNPE (Snapdragon Neural Processing Engine) network,
 * loads the model from assets, prepares input tensors, and manages bitmap preprocessing.
 *
 * @param application The application context used for accessing assets and other system resources.
 * @param config The configuration details for the model, including file name, input/output layer
 *      names, and input dimensions.
 * @param isUnsignedPD Flag indicating whether the input data format is unsigned (default is true).
 */
class SnpeModel(
    private val application: Application,
    private val config: ModelConfig,
    private val isUnsignedPD: Boolean = true,
) : Closeable,
    InferenceEngine<TensorOutputs> {
    /**
     * A set of private constants used in this class.
     */
    companion object {
        private val TAG = SnpeModel::class.qualifiedName

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
         * Neutral gray value for normalization.
         */
        private const val NEUTRAL_GRAY_VALUE = 0.5f
    }

    /**
     * The neural network instance.
     */
    private lateinit var neuralNetwork: NeuralNetwork

    /**
     * The input tensor used to store preprocessed image data.
     */
    private lateinit var inputTensor: FloatTensor

    /**
     * The input map of tensor names to their corresponding FloatTensor values.
     */
    private lateinit var inputMap: Map<String, FloatTensor>

    /**
     * Utility object for bitmap-to-buffer preprocessing operations.
     */
    private val bitmapRgbFloatPreprocessor = BitmapRgbFloatPreprocessor()

    /**
     * Input width dimension.
     */
    private val inputW = config.inputNHWC[2]

    /**
     * Input height dimension.
     */
    private val inputH = config.inputNHWC[1]

    /**
     * Destination rectangle for bitmap resizing.
     */
    private val dstRect = Rect(0, 0, inputW, inputH)

    /**
     * Paint object with bitmap filtering enabled.
     */
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Reusable bitmap and canvas for input preprocessing.
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
     * Lock object for synchronizing state changes.
     */
    private val stateLock = Any()

    /**
     * Initializes the model by loading it from assets with retry logic. This method should be
     * called before using the model for inference.
     *
     * **Thread Safety:**
     * It is safe to call this method multiple times. Subsequent calls will be ignored if the
     * model is already successfully initialized or if it has been closed.
     *
     * **Recovery After Inference Failure:**
     * This method can be called to reinitialize the model after an inference failure. When
     * [infer] encounters an exception, it releases native resources and marks the model as
     * uninitialized. Calling this method again will reload the model from assets, allowing
     * the application to recover without requiring a complete restart.
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
                        Log.d(TAG, "Attempting to load model ${config.fileName} ($attempt/$MAX_RETRIES)")

                        if (loadModel()) {
                            isInitialized = true
                            successfullyLoaded = true
                            Log.i(TAG, "Model initialized successfully: ${config.fileName}")
                            break
                        }

                        Log.w(TAG, "Model loading returned false (attempt $attempt)")
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "Model loading exception (attempt $attempt): ${e.message}")
                    }

                    if (attempt < MAX_RETRIES && !successfullyLoaded) {
                        delay(currentDelay)
                        currentDelay = (currentDelay * 2).coerceAtMost(MAX_DELAY_MS)
                    }
                }

                if (!successfullyLoaded) {
                    val errorMsg = "Failed to load model ${config.fileName} after $MAX_RETRIES attempts"
                    Log.e(TAG, errorMsg, lastException)
                    throw IllegalStateException(errorMsg, lastException)
                }
            }
        }
    }

    /**
     * Loads the DLC model from assets using the DSP runtime and initializes input tensors.
     *
     * This method attempts to load the model file from assets using the DSP runtime, initializes
     * the input tensor shape and buffer, and prepares the input tensor map.
     *
     * @return `true` if the model was successfully loaded and initialized; `false` otherwise.
     */
    private fun loadModel(): Boolean {
        // Load the DLC model from assets. If loading fails, return false.
        val model = loadModelFromAssets() ?: return false

        // Creates the input tensor based on the model configuration.
        val tensor = model.createFloatTensor(*config.inputNHWC.toIntArray()) ?: return false

        // Creates the inputMap with the non-null input tensor, updates the neural network reference
        // and updates the input tensor reference.
        inputMap = mapOf(config.inputLayerName to tensor)
        neuralNetwork = model
        inputTensor = tensor

        // Perform a warmup run to initialize internal states. If warmup fails, release resources
        // and return false.
        if (!warmup()) {
            runCatching { inputTensor.release() }
            runCatching { neuralNetwork.release() }
            return false
        }

        return true
    }

    /**
     * Loads the DLC model from the application's asset folder.
     *
     * This method initializes a DLC [NeuralNetwork] instance using the DSP runtime configuration.
     * It sets the runtime check option based on the input format (unsigned or not), specifies
     * output layers, and disable CPU fallback. If an error occurs during model loading, it logs
     * the exception and returns null.
     *
     * @return A configured [NeuralNetwork] instance if loading succeeds, or `null` if an error
     *      occurs.
     */
    private fun loadModelFromAssets(): NeuralNetwork? =
        try {
            application.assets.open(config.fileName).use { stream ->
                val outputLayers = config.outputLayerNames.toTypedArray()
                val model =
                    SNPE
                        .NeuralNetworkBuilder(application)
                        .setRuntimeCheckOption(
                            if (isUnsignedPD) {
                                NeuralNetwork.RuntimeCheckOption.UNSIGNEDPD_CHECK
                            } else {
                                NeuralNetwork.RuntimeCheckOption.NORMAL_CHECK
                            },
                        ).setOutputLayers(*outputLayers)
                        .setModel(stream, stream.available())
                        .setPerformanceProfile(NeuralNetwork.PerformanceProfile.DEFAULT)
                        .setRuntimeOrder(NeuralNetwork.Runtime.DSP)
                        .setCpuFallbackEnabled(false)
                        .build()
                model
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model loading error", e)
            null
        }

    /**
     * Performs a warmup run of the neural network to initialize internal states.
     *
     * This method creates a dummy input tensor filled with non-zero values, writes it to the
     * input tensor, and executes the neural network. It ensures that all output tensors are
     * released after execution. If any exception occurs during the process, it logs a warning
     * and returns false.
     *
     * @return `true` if the warmup run completes successfully; `false` otherwise.
     */
    private fun warmup(): Boolean =
        try {
            val size = inputW * inputH * CHANNELS_RGB
            val warm = FloatArray(size) { NEUTRAL_GRAY_VALUE }

            inputTensor.write(warm, 0, warm.size, 0)

            val outputs = neuralNetwork.execute(inputMap)
            try {
                true
            } finally {
                outputs.values.forEach { t -> runCatching { t.release() } }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warmup failed for model ${config.fileName}", e)
            false
        }

    /**
     * Runs the DLC neural network model on a preprocessed and resized bitmap.
     *
     * This method runs inference synchronously on the current thread.
     *
     * **Error Recovery Behavior:**
     * If inference fails due to an exception, this method will:
     * 1. Mark the model as uninitialized (isInitialized = false)
     * 2. Release native resources (input tensor and neural network)
     * 3. Return null to indicate failure
     *
     * After an inference failure, the model enters a recoverable state where:
     * - The model is not closed (isClosed remains false)
     * - Native resources are released to prevent memory leaks
     * - The model can be reinitialized by calling [initialize] again
     *
     * The [initialize] method will reload the model from assets with retry logic,
     * allowing the application to recover from transient failures without requiring
     * a complete restart.
     *
     * @param bitmap The original input image to be processed.
     * @param block A lambda function that consumes the output tensors.
     *
     * @return The result of the block function, or null if inference fails.
     */
    override fun <R> infer(
        bitmap: Bitmap,
        block: (TensorOutputs) -> R,
    ): R? {
        if (isClosed || !isInitialized) {
            return null
        }

        return try {
            executeInference(bitmap, block)
        } catch (e: Exception) {
            Log.e(TAG, "Inference execution failed", e)

            // Enter recoverable error state: mark as uninitialized and release native resources.
            // The model can be reinitialized by calling initialize() again.
            // Use synchronized block to atomically update state and release resources.
            synchronized(stateLock) {
                isInitialized = false
                runCatching { if (::inputTensor.isInitialized) inputTensor.release() }
                runCatching { if (::neuralNetwork.isInitialized) neuralNetwork.release() }
            }

            null
        }
    }

    /**
     * Executes the actual inference operations synchronously.
     *
     * This method performs input validation, bitmap preprocessing, tensor writing, and invokes the
     * neural network's execute method. It ensures that native resources are released after the
     * block function is executed.
     *
     * Thread-safe: Uses synchronized block to protect access to native resources (inputTensor and
     * neuralNetwork) from concurrent modification during resource release.
     *
     * @param bitmap The input bitmap to be processed.
     * @param block A lambda function that processes the output tensors.
     *
     * @return The result of the block function, or null if input validation fails.
     */
    private fun <R> executeInference(
        bitmap: Bitmap,
        block: (TensorOutputs) -> R,
    ): R? {
        val inputBmp = getReusableInput(bitmap)

        // 1. Fast-path state and dimension validation without synchronization.
        // This provides an early exit before expensive preprocessing operations.
        val isStateValid =
            !isClosed &&
                isInitialized &&
                inputBmp.width == config.inputNHWC[2] &&
                inputBmp.height == config.inputNHWC[1]

        if (!isStateValid) return null

        // 2. Preprocess the bitmap and convert to a float buffer.
        bitmapRgbFloatPreprocessor.convertBitmapToBuffer(inputBmp)
        val floats = bitmapRgbFloatPreprocessor.bufferToFloatsRGB()

        // 3. Return null if the last buffer was black (all zeros), otherwise execute inference.
        return if (bitmapRgbFloatPreprocessor.wasLastBufferBlack()) {
            null
        } else {
            // Synchronize access to native resources to prevent race conditions with
            // close() method and error recovery in infer() method.
            synchronized(stateLock) {
                if (!isClosed && isInitialized) {
                    inputTensor.write(floats, 0, floats.size, 0)
                    val outputs: TensorOutputs = neuralNetwork.execute(inputMap)
                    try {
                        block(outputs)
                    } finally {
                        outputs.values.forEach { t -> runCatching { t.release() } }
                    }
                } else {
                    null
                }
            }
        }
    }

    /**
     * Retrieves a reusable input bitmap resized to the model's input dimensions.
     *
     * This method checks if a reusable bitmap and canvas already exist. If not, it creates them.
     * It then draws the source bitmap onto the reusable bitmap using the predefined destination
     * rectangle and paint settings.
     *
     * @param src The source bitmap to be resized.
     *
     * @return A bitmap resized to the model's input dimensions.
     */
    private fun getReusableInput(src: Bitmap): Bitmap {
        val bmp =
            reusableInputBitmap ?: createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888)
                .also {
                    reusableInputBitmap = it
                    reusableCanvas = Canvas(it)
                }

        reusableCanvas?.drawBitmap(src, null, dstRect, paint)
        return bmp
    }

    /**
     * Releases resources associated with the current DLC neural network instance.
     *
     * This method safely releases the underlying native resources and clears all references to the
     * model, input tensor, and input map to avoid memory leaks.
     *
     * Thread-safe: Uses synchronized block to prevent concurrent access to native resources
     * during resource release.
     */
    override fun close() {
        synchronized(stateLock) {
            if (isClosed) return
            isClosed = true

            runCatching { if (::inputTensor.isInitialized) inputTensor.release() }
            runCatching { if (::neuralNetwork.isInitialized) neuralNetwork.release() }
        }

        // Help GC (not mandatory)
        reusableCanvas = null
        reusableInputBitmap = null
    }
}
