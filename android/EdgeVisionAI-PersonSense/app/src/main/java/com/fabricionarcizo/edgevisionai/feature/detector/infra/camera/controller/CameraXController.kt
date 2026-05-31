package com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.controller

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.CameraStartRequest
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.CameraStartResult
import com.fabricionarcizo.edgevisionai.feature.detector.infra.image.ImageProxyToBitmapConverter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller that binds the CameraX `Preview` + `ImageAnalysis` use cases to a
 * provided `LifecycleOwner`. Latest frame goes into [LatestFrameStore]; the
 * ViewModel reads from there when the user taps Capture.
 *
 * Singleton so the analysis executor is shared across configuration changes.
 */
@Singleton
class CameraXController @Inject constructor(
    private val converter: ImageProxyToBitmapConverter,
    private val frameStore: LatestFrameStore,
) {
    private var analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null

    /** Bind the camera pipeline. Idempotent — unbinds previous bindings first. */
    fun start(request: CameraStartRequest): CameraStartResult {
        val provider = request.cameraProvider
        if (!provider.hasCamera(request.cameraSelector)) {
            return CameraStartResult.Failed("Requested camera is not available.")
        }
        return try {
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(request.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { ia ->
                    ia.setAnalyzer(analyzerExecutor) { proxy -> handleFrame(proxy) }
                }
            imageAnalysis = analysis

            provider.bindToLifecycle(
                request.lifecycleOwner,
                request.cameraSelector,
                preview,
                analysis,
            )
            CameraStartResult.Started
        } catch (t: Throwable) {
            Log.e(TAG, "Camera bind failed", t)
            CameraStartResult.Failed("Camera error: ${t.message}")
        }
    }

    /** Detach the analyzer so frames stop flowing. */
    fun stop() {
        try {
            imageAnalysis?.clearAnalyzer()
        } catch (_: Throwable) {
            // Best-effort.
        }
        imageAnalysis = null
    }

    /** Hard release — call from Application/Activity onDestroy. */
    fun release() {
        stop()
        frameStore.clear()
        if (!analyzerExecutor.isShutdown) analyzerExecutor.shutdown()
        analyzerExecutor = Executors.newSingleThreadExecutor()
    }

    private fun handleFrame(proxy: ImageProxy) {
        try {
            val bitmap = converter.convert(proxy) ?: return
            frameStore.set(bitmap)
        } catch (t: Throwable) {
            Log.w(TAG, "frame conversion failed", t)
        } finally {
            proxy.close()
        }
    }

    private companion object {
        const val TAG = "CameraXController"
    }
}
