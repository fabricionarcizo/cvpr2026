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
import com.fabricionarcizo.edgevisionai.ml.api.InferenceEngine
import com.fabricionarcizo.edgevisionai.ml.api.TensorOutputs
import com.fabricionarcizo.edgevisionai.ml.config.ModelConfig
import com.fabricionarcizo.edgevisionai.ml.preprocess.BitmapRgbFloatPreprocessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File

/**
 * A class for managing QNN context binary (.bin) model inference using native QNN C++ APIs
 * through a JNI bridge.
 *
 * This class replaces the SNPE-based implementation with direct QNN API calls via
 * [qnn_inference_jni] native library. The HTP/DSP backend is used via `libQnnHtp.so`,
 * loaded dynamically at runtime with `dlopen`.
 *
 * **Threading:**
 * QNN contexts are not thread-safe. All native calls are serialized through [stateLock].
 * If concurrent inference is needed, create separate [QnnModel] instances per thread — each
 * maintains its own `QnnContext_Handle_t`.
 *
 * @param application The application context used for accessing assets and `filesDir`.
 * @param config The configuration details for the model, including file name, input/output layer
 *      names, and input dimensions.
 */
class QnnModel(
    private val application: Application,
    private val config: ModelConfig,
) : Closeable,
    InferenceEngine<TensorOutputs> {

    companion object {
        private val TAG = QnnModel::class.qualifiedName

        /** Maximum number of initialization retry attempts. */
        private const val MAX_RETRIES = 5

        /** Initial delay in milliseconds before first retry. */
        private const val INITIAL_DELAY_MS = 500L

        /** Maximum delay in milliseconds between retries. */
        private const val MAX_DELAY_MS = 5000L

        /** Number of color channels in RGB images. */
        private const val CHANNELS_RGB = 3

        /** Neutral gray value matching the YOLOX letterbox pad value (0–255 scale). */
        private const val NEUTRAL_GRAY_VALUE = 114f

        /** Letterbox padding color (matches YOLOX convention: constant 114 gray). */
        private const val LETTERBOX_PAD = 114

        init {
            // Load the JNI bridge library. libQnnHtp.so and libQnnSystem.so are loaded
            // dynamically inside the native code via dlopen at nativeInit() time.
            System.loadLibrary("qnn_inference_jni")
        }
    }

    // -------------------------------------------------------------------------
    // JNI declarations — implemented in qnn_inference_jni.cpp
    // -------------------------------------------------------------------------

    /**
     * Initialises the QNN backend and loads the context binary from [modelPath].
     *
     * Internally this:
     *  1. `dlopen`s `libQnnHtp.so` and `libQnnSystem.so`
     *  2. Resolves `QnnInterface_getProviders` and selects the HTP provider
     *  3. Calls `QnnBackend_create` / `QnnContext_createFromBinary` / `QnnGraph_retrieve`
     *  4. Allocates input/output tensor buffers
     *
     * @param modelPath Absolute path to the `.bin` context binary on the device filesystem.
     * @param outputNames Ordered array of output tensor names (e.g. ["bboxes", "scores"]).
     * @return An opaque `jlong` handle to the native [NativeQnnContext] struct, or 0 on failure.
     */
    private external fun nativeInit(modelPath: String, outputNames: Array<String>): Long

    /**
     * Executes one inference pass through `QnnGraph_execute`.
     *
     * The [input] array must be exactly `N * C * H * W` floats in NCHW planar order, matching
     * [ModelConfig.inputNCHW]. INT8 output tensors are dequantized to `float32` inside the
     * native layer using the embedded `scaleOffsetEncoding` quantization parameters.
     *
     * @param handle Native handle returned by [nativeInit].
     * @param input  Preprocessed input data as a flat float array (NCHW layout).
     * @return Array of float arrays, one per output tensor, in the same order as [outputNames]
     *      passed to [nativeInit]. Returns an empty array on failure.
     */
    private external fun nativeRun(handle: Long, input: FloatArray): Array<FloatArray>

    /**
     * Releases all native QNN resources associated with [handle].
     *
     * Calls `QnnContext_free`, `QnnBackend_free`, `dlclose` on both loaded libraries, and
     * deletes the underlying C++ struct.
     *
     * @param handle Native handle returned by [nativeInit].
     */
    private external fun nativeRelease(handle: Long)

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Opaque pointer to the C++ NativeQnnContext. 0 means uninitialised. */
    private var nativeHandle: Long = 0L

    /** Utility object for bitmap-to-buffer preprocessing operations. */
    private val bitmapRgbFloatPreprocessor = BitmapRgbFloatPreprocessor()

    /** Input width dimension. */
    private val inputW = config.inputNCHW[3]

    /** Input height dimension. */
    private val inputH = config.inputNCHW[2]

    /** Paint object with bitmap filtering enabled. */
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Reusable bitmap for letterbox preprocessing. */
    private var reusableInputBitmap: Bitmap? = null

    /** Reusable canvas for letterbox preprocessing. */
    private var reusableCanvas: Canvas? = null

    /** True after [close] has been called. */
    @Volatile
    private var isClosed: Boolean = false

    /** True after a successful [loadModel] call. */
    @Volatile
    private var isInitialized: Boolean = false

    /** Serialises concurrent [initialize] calls. */
    private val initMutex = Mutex()

    /** Serialises all accesses to [nativeHandle] (QNN contexts are not thread-safe). */
    private val stateLock = Any()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Initializes the model by loading it from assets with exponential-backoff retry logic.
     *
     * Safe to call multiple times — subsequent calls are no-ops once the model is initialized
     * or closed. Can be called again after an inference failure to recover the model.
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
                        Log.d(TAG, "Loading model ${config.fileName} (attempt $attempt/$MAX_RETRIES)")
                        if (loadModel()) {
                            isInitialized = true
                            successfullyLoaded = true
                            Log.i(TAG, "QnnModel initialized successfully: ${config.fileName}")
                            break
                        }
                        Log.w(TAG, "loadModel() returned false (attempt $attempt)")
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "loadModel() exception (attempt $attempt): ${e.message}")
                    }

                    if (attempt < MAX_RETRIES) {
                        delay(currentDelay)
                        currentDelay = (currentDelay * 2).coerceAtMost(MAX_DELAY_MS)
                    }
                }

                if (!successfullyLoaded) {
                    val msg = "Failed to load model ${config.fileName} after $MAX_RETRIES attempts"
                    Log.e(TAG, msg, lastException)
                    throw IllegalStateException(msg, lastException)
                }
            }
        }
    }

    /**
     * Runs QNN inference on [bitmap] and passes the output tensors to [block].
     *
     * If inference fails, the model is marked as uninitialised and native resources are
     * released. Call [initialize] again to recover.
     *
     * @param bitmap The original input image to be processed.
     * @param block  A lambda that consumes the [TensorOutputs] map.
     * @return The result of [block], or `null` if inference cannot be executed.
     */
    override fun <R> infer(bitmap: Bitmap, block: (TensorOutputs) -> R): R? {
        if (isClosed || !isInitialized) return null

        return try {
            executeInference(bitmap, block)
        } catch (e: Exception) {
            Log.e(TAG, "Inference execution failed", e)
            synchronized(stateLock) {
                isInitialized = false
                releaseNativeHandle()
            }
            null
        }
    }

    /**
     * Releases all QNN and bitmap resources.
     *
     * Thread-safe via [stateLock].
     */
    override fun close() {
        synchronized(stateLock) {
            if (isClosed) return
            isClosed = true
            releaseNativeHandle()
        }
        reusableCanvas = null
        reusableInputBitmap = null
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Copies the model asset to [Application.filesDir] (if not already present), calls
     * [nativeInit], and runs a warmup inference. Must be called under [initMutex].
     *
     * @return `true` on success.
     */
    private fun loadModel(): Boolean {
        val modelPath = copyAssetToFilesDir(config.fileName) ?: return false

        // Copy the HTP V68 skel library to filesDir so the ADSP loader can find it.
        // nativeInit() will set ADSP_LIBRARY_PATH to the parent directory of modelPath.
        // Failure to copy is non-fatal: the skel may already be in /vendor/lib/rfsa/adsp/.
        tryCopyAssetToFilesDir("libQnnHtpV68Skel.so")

        val outputNamesArray = config.outputLayerNames.toTypedArray()
        Log.d(TAG, "loadModel() calling nativeInit: modelPath=$modelPath outputs=${outputNamesArray.toList()}")
        val handle = nativeInit(modelPath, outputNamesArray)
        Log.d(TAG, "loadModel() nativeInit returned handle=$handle")
        if (handle == 0L) {
            Log.e(TAG, "nativeInit returned 0 for ${config.fileName}")
            return false
        }

        synchronized(stateLock) { nativeHandle = handle }
        Log.d(TAG, "loadModel() nativeHandle stored, starting warmup")

        if (!warmup()) {
            Log.e(TAG, "Warmup failed for ${config.fileName}")
            synchronized(stateLock) { releaseNativeHandle() }
            return false
        }

        return true
    }

    /**
     * Copies [assetName] from `assets/` to [Application.filesDir] and returns the absolute path.
     * Skips the copy if the destination file already exists and is non-empty.
     *
     * @return The absolute path string, or `null` on I/O error.
     */
    private fun copyAssetToFilesDir(assetName: String): String? {
        val destFile = File(application.filesDir, assetName)
        if (destFile.exists() && destFile.length() > 0) {
            Log.d(TAG, "Model already on disk: ${destFile.absolutePath}")
            return destFile.absolutePath
        }
        return try {
            application.assets.open(assetName).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "Copied ${assetName} to ${destFile.absolutePath} (${destFile.length()} bytes)")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset $assetName to filesDir", e)
            null
        }
    }

    /**
     * Silently copies [assetName] to [Application.filesDir] if the asset exists.
     * Does nothing (no error) if the asset is absent — useful for optional files
     * like DSP skel libraries that may or may not be bundled in the APK.
     */
    private fun tryCopyAssetToFilesDir(assetName: String) {
        try {
            val destFile = File(application.filesDir, assetName)
            if (destFile.exists() && destFile.length() > 0) return
            application.assets.open(assetName).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "Copied skel $assetName → ${destFile.absolutePath}")
        } catch (_: Exception) {
            // Asset not present or copy failed — ADSP will fall back to vendor path
        }
    }

    /**
     * Performs a warmup inference using a constant-gray input to warm up HTP internal state.
     *
     * @return `true` if the warmup run completes without error.
     */
    private fun warmup(): Boolean {
        Log.d(TAG, "warmup() start: inputW=$inputW inputH=$inputH channels=$CHANNELS_RGB")
        return try {
            val size = inputW * inputH * CHANNELS_RGB
            Log.d(TAG, "warmup() allocating FloatArray(size=$size)")
            val warmInput = FloatArray(size) { NEUTRAL_GRAY_VALUE }
            Log.d(TAG, "warmup() FloatArray allocated OK")
            val handle = synchronized(stateLock) { nativeHandle }
            Log.d(TAG, "warmup() calling nativeRun(handle=$handle, inputLen=${warmInput.size})")
            val outputs = nativeRun(handle, warmInput)
            Log.i(TAG, "Warmup succeeded; output tensors: ${outputs.size}")
            outputs.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Warmup failed", e)
            false
        }
    }

    /**
     * Core inference path: preprocess [bitmap], call [nativeRun], package outputs as
     * [TensorOutputs].
     */
    private fun <R> executeInference(bitmap: Bitmap, block: (TensorOutputs) -> R): R? {
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
            if (!isClosed && isInitialized) {
                val rawOutputs = nativeRun(nativeHandle, floats)
                // Zip output names → float arrays to produce the TensorOutputs map.
                val tensorOutputs: TensorOutputs = config.outputLayerNames
                    .zip(rawOutputs.toList())
                    .toMap()
                block(tensorOutputs)
            } else {
                null
            }
        }
    }

    /**
     * Builds (or reuses) a [inputW] × [inputH] bitmap with the YOLOX letterbox convention:
     * the source is scaled to fit while preserving aspect ratio, placed top-left, remainder
     * filled with RGB(114, 114, 114).
     */
    private fun getReusableInput(src: Bitmap): Bitmap {
        val bmp = reusableInputBitmap
            ?: createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888).also {
                reusableInputBitmap = it
                reusableCanvas = Canvas(it)
            }

        val ratio = minOf(inputW.toFloat() / src.width, inputH.toFloat() / src.height)
        val scaledW = (src.width * ratio).toInt()
        val scaledH = (src.height * ratio).toInt()

        bmp.eraseColor(android.graphics.Color.rgb(LETTERBOX_PAD, LETTERBOX_PAD, LETTERBOX_PAD))
        reusableCanvas?.drawBitmap(src, null, Rect(0, 0, scaledW, scaledH), paint)
        return bmp
    }

    /**
     * Releases the native handle if non-zero. Must be called within [stateLock].
     */
    private fun releaseNativeHandle() {
        if (nativeHandle != 0L) {
            try {
                nativeRelease(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "nativeRelease threw", e)
            }
            nativeHandle = 0L
        }
    }
}
