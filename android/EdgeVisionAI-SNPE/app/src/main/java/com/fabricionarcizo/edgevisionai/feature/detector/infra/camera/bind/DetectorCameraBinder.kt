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
package com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.bind

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.config.DetectorCameraConfig
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * Binds camera use cases for the detector feature based on the provided configuration.
 *
 * @property config The configuration settings for the detector camera.
 */
class DetectorCameraBinder(
    private val config: DetectorCameraConfig,
) : Closeable {
    /**
     * Executor for image analysis tasks.
     */
    private var analysisExecutor = Executors.newSingleThreadExecutor()

    /**
     * Closes the binder and releases resources.
     */
    override fun close() {
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    /**
     * Binds the camera use cases to the lifecycle owner with the specified parameters.
     *
     * @param lifecycleOwner The lifecycle owner to which the camera use cases will be bound.
     * @param cameraProvider The camera provider for accessing camera functionalities.
     * @param previewView The preview view where the camera feed will be displayed.
     * @param cameraSelector The selector to choose which camera to use (e.g., front or back).
     * @param analyzer A lambda function that processes each frame from the camera.
     *
     * @return The configured ImageAnalysis use case.
     */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        cameraProvider: ProcessCameraProvider,
        previewView: PreviewView,
        cameraSelector: CameraSelector,
        analyzer: (ImageProxy) -> Unit,
    ): ImageAnalysis {
        val resolutionStrategy =
            ResolutionStrategy(
                config.targetResolution,
                config.fallbackRule,
            )

        val resolutionSelector =
            ResolutionSelector
                .Builder()
                .setResolutionStrategy(resolutionStrategy)
                .setAspectRatioStrategy(config.aspectRatioStrategy)
                .setAllowedResolutionMode(config.allowedResolutionMode)
                .build()

        val targetRotation = previewView.display?.rotation ?: config.defaultRotation

        val imageAnalysis =
            ImageAnalysis
                .Builder()
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(config.backpressureStrategy)
                .setImageQueueDepth(config.depth)
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(config.outputImageFormat)
                .build()
                .also { ia ->
                    ia.setAnalyzer(analysisExecutor) { imageProxy ->
                        analyzer(imageProxy)
                    }
                }

        val preview =
            Preview
                .Builder()
                .setTargetRotation(targetRotation)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { p ->
                    p.surfaceProvider = previewView.surfaceProvider
                }

        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis,
        )

        return imageAnalysis
    }
}
