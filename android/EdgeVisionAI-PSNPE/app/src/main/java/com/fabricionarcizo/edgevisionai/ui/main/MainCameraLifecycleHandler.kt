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
package com.fabricionarcizo.edgevisionai.ui.main

import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.LifecycleOwner
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorCameraStartRequest
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.ResultsOverlayView
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.viewmodel.DetectorViewModel

/**
 * A composable function that handles the lifecycle of the camera.
 *
 * @param detectorViewModel The view model that manages the state of the detector.
 * @param hasCameraPermission A boolean value indicating whether the camera permission is granted.
 * @param isModelLoaded A boolean value indicating whether the model is loaded.
 * @param errorMessage The error message if any.
 * @param lifecycleOwner The lifecycle owner.
 * @param cameraProvider The camera provider.
 * @param previewView The view that displays the camera preview.
 * @param resultsOverlayView The view that displays the object detection results.
 * @param cameraSelector The camera selector.
 */
@Composable
fun MainCameraLifecycleHandler(
    detectorViewModel: DetectorViewModel,
    hasCameraPermission: Boolean,
    isModelLoaded: Boolean,
    errorMessage: String?,
    lifecycleOwner: LifecycleOwner,
    cameraProvider: ProcessCameraProvider?,
    previewView: PreviewView,
    resultsOverlayView: ResultsOverlayView,
    cameraSelector: CameraSelector,
) {
    DisposableEffect(Unit) {
        onDispose {
            detectorViewModel.stopCamera()
        }
    }

    LaunchedEffect(
        hasCameraPermission,
        cameraProvider,
        isModelLoaded,
        errorMessage,
        lifecycleOwner,
    ) {
        detectorViewModel.stopCamera()

        val provider = cameraProvider ?: return@LaunchedEffect
        if (!hasCameraPermission || !isModelLoaded || errorMessage != null) return@LaunchedEffect

        detectorViewModel.startCamera(
            DetectorCameraStartRequest(
                lifecycleOwner = lifecycleOwner,
                cameraProvider = provider,
                previewView = previewView,
                cameraSelector = cameraSelector,
                onResults = { results ->
                    resultsOverlayView.update(results)
                },
            ),
        )
    }
}
