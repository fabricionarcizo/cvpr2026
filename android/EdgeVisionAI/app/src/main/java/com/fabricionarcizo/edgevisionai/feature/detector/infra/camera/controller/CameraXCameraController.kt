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
package com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.controller

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.FrameAnalyzer
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.FrameSource
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.bind.DetectorCameraBinder
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.CameraStartResult
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorCameraStartRequest
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorFramePayload
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorPayloadCallback
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.FrameInfo
import com.fabricionarcizo.edgevisionai.feature.detector.infra.image.FrameTransformer
import com.fabricionarcizo.edgevisionai.feature.detector.infra.image.ImageProxyToBitmapConverter
import com.fabricionarcizo.edgevisionai.feature.detector.infra.perf.FPSTracker
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.config.DetectorCameraConfig

/**
 * Camera controller implementation using CameraX.
 *
 * @property cameraConfig Configuration for the detector camera.
 * @property imageProxyToBitmapConverter Converter to convert ImageProxy to RGBA Bitmap.
 * @property frameTransformer Transformer to apply transformations to frames.
 * @property frameAnalyzer Analyzer to process frames and obtain results.
 * @property mainHandler Handler for posting results to the main thread.
 */
class CameraXCameraController(
    cameraConfig: DetectorCameraConfig,
    private val imageProxyToBitmapConverter: ImageProxyToBitmapConverter,
    private val frameTransformer: FrameTransformer,
    private val frameAnalyzer: FrameAnalyzer,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : FrameSource {
    /**
     * Companion object holding constants.
     */
    companion object {
        /**
         * Logging tag for the CameraXCameraController class.
         */
        private val TAG = CameraXCameraController::class.qualifiedName

        /**
         * Constant to convert nanoseconds to milliseconds (10^6).
         */
        private const val NANOS_TO_MILLIS = 1_000_000L

        /**
         * Default number of frames between metrics updates.
         */
        private const val METRICS_EVERY_N_FRAMES = 10L
    }

    /**
     * Binder for managing camera use cases.
     */
    private val cameraBinder =
        DetectorCameraBinder(
            cameraConfig,
        )

    /**
     * Currently selected camera.
     */
    private var cameraSelector: CameraSelector? = null

    /**
     * Image analysis use case.
     */
    private var imageAnalysisUseCase: ImageAnalysis? = null

    /**
     * Frame counter.
     */
    private var frameCounter: Long = 0L

    /**
     * Last frame timing information.
     */
    private var lastTiming: FrameTiming? = null

    /**
     * FPS tracker to monitor frames per second.
     */
    private val fpsTracker = FPSTracker()

    /**
     * Data class to hold timing information for frame processing.
     *
     * @param convertMs Time taken to convert the image (in milliseconds).
     * @param transformMs Time taken to transform the frame (in milliseconds).
     * @param inferenceMs Time taken for inference (in milliseconds).
     * @param totalMs Total time taken for processing the frame (in milliseconds).
     */
    private data class FrameTiming(
        val convertMs: Long,
        val transformMs: Long,
        val inferenceMs: Long,
        val totalMs: Long,
    )

    /**
     * Starts the camera with the given request parameters.
     *
     * @param request The request parameters for starting the camera.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun start(request: DetectorCameraStartRequest): CameraStartResult {
        val resolvedSelector =
            try {
                resolveCameraSelector(request)
            } catch (e: Exception) {
                Log.e(TAG, "Camera availability check failed: ${e.message}", e)
                null
            }

        val result =
            if (resolvedSelector == null) {
                CameraStartResult.Failed(
                    "Requested camera or availability check failed.",
                )
            } else {
                cameraSelector = resolvedSelector

                try {
                    imageAnalysisUseCase =
                        cameraBinder.bind(
                            lifecycleOwner = request.lifecycleOwner,
                            cameraProvider = request.cameraProvider,
                            previewView = request.previewView,
                            cameraSelector = resolvedSelector,
                        ) { imageProxy ->
                            analyzeFrame(imageProxy, request.onResults)
                        }
                    CameraStartResult.Started
                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                    CameraStartResult.Failed(
                        "Camera binding failed: ${e.message}.",
                    )
                }
            }

        return result
    }

    /**
     * Stops the camera.
     */
    override fun stop() {
        try {
            imageAnalysisUseCase?.clearAnalyzer()
        } catch (_: Throwable) {
            // ignore
        }
        imageAnalysisUseCase = null
        cameraSelector = null
    }

    /**
     * Resolves the camera selector based on the request, with fallback options.
     *
     * @param request The camera start request containing the desired camera selector.
     *
     * @return The resolved CameraSelector, or null if no suitable camera is found.
     */
    private fun resolveCameraSelector(request: DetectorCameraStartRequest): CameraSelector? {
        val provider: ProcessCameraProvider = request.cameraProvider

        val fallbackPriority: List<CameraSelector> =
            when (val requestedSelector = request.cameraSelector) {
                CameraSelector.DEFAULT_FRONT_CAMERA ->
                    listOf(
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                    )

                CameraSelector.DEFAULT_BACK_CAMERA ->
                    listOf(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                    )

                else -> listOf(requestedSelector)
            }

        for (selector in fallbackPriority) {
            if (provider.hasCamera(selector)) {
                return selector
            }
        }

        return null
    }

    /**
     * Analyzes a single frame from the camera.
     *
     * @param imageProxy The image frame to analyze.
     * @param onResults Callback to deliver the analysis results.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun analyzeFrame(
        imageProxy: ImageProxy,
        onResults: DetectorPayloadCallback,
    ) {
        try {
            val t0 = SystemClock.elapsedRealtimeNanos()

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // 1) Convert ImageProxy -> Bitmap.
            val tConv0 = t0
            val rawBitmap = imageProxyToBitmapConverter.toBitmap(imageProxy)
            val tConv1 = SystemClock.elapsedRealtimeNanos()

            // 2) Transform (rotate to display orientation).
            val tTf0 = tConv1
            val bitmap = frameTransformer.transform(rawBitmap, rotationDegrees)
            val tTf1 = SystemClock.elapsedRealtimeNanos()

            // 3) Pipeline inference.
            val tInf0 = tTf1
            val frameOut = frameAnalyzer.process(bitmap)
            val tInf1 = SystemClock.elapsedRealtimeNanos()

            val convertMs = (tConv1 - tConv0) / NANOS_TO_MILLIS
            val transformMs = (tTf1 - tTf0) / NANOS_TO_MILLIS
            val inferenceMs = frameOut.inferenceMs.takeIf { it >= 0 } ?: ((tInf1 - tInf0) / NANOS_TO_MILLIS)
            val totalMs = (tInf1 - t0) / NANOS_TO_MILLIS

            // Throttle: update metrics only every N frames.
            val shouldUpdateMetrics = (++frameCounter % METRICS_EVERY_N_FRAMES == 0L)
            if (shouldUpdateMetrics) {
                lastTiming =
                    FrameTiming(
                        convertMs = convertMs,
                        transformMs = transformMs,
                        inferenceMs = inferenceMs,
                        totalMs = totalMs,
                    )
            }

            val detections = frameOut.detections
            val isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA

            fpsTracker.frameProcessed()
            val fps = fpsTracker.fps

            mainHandler.post {
                onResults(
                    DetectorFramePayload(
                        detections = detections,
                        frame =
                            FrameInfo(
                                width = bitmap.width,
                                height = bitmap.height,
                                isFrontCamera = isFrontCamera,
                            ),
                        fps = fps.takeIf { it > 0f },
                    ),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during frame processing", e)
        } finally {
            imageProxy.close()
        }
    }
}
