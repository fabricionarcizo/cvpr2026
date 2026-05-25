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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.viewmodel.DetectorViewModel

/**
 * A composable function that represents the main screen of the application.
 *
 * @param detectorViewModel The view model that manages the state of the detector.
 * @param hasCameraPermission A boolean value indicating whether the camera permission is granted.
 */
@Composable
fun MainScreen(
    detectorViewModel: DetectorViewModel,
    hasCameraPermission: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by detectorViewModel.uiState.collectAsStateWithLifecycle()

    var confidenceThreshold by remember { mutableFloatStateOf(0.5f) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember(context) { createPreviewView(context) }
    val resultsOverlayView = remember(context) { createResultsOverlayView(context) }

    ConfigureOverlayStyle(resultsOverlayView)

    LaunchedEffect(context) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cameraProvider = future.get() },
            ContextCompat.getMainExecutor(context),
        )
    }

    MainCameraLifecycleHandler(
        detectorViewModel = detectorViewModel,
        hasCameraPermission = hasCameraPermission,
        isModelLoaded = uiState.isModelLoaded,
        errorMessage = uiState.errorMessage,
        lifecycleOwner = lifecycleOwner,
        cameraProvider = cameraProvider,
        previewView = previewView,
        resultsOverlayView = resultsOverlayView,
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    )

    LaunchedEffect(confidenceThreshold) {
        detectorViewModel.updateObjectThreshold(confidenceThreshold)
    }

    MainContent(
        previewView = previewView,
        resultsOverlayView = resultsOverlayView,
        confidenceThreshold = confidenceThreshold,
        onConfidenceThresholdChange = { confidenceThreshold = it },
        fps = uiState.fps,
    )
}
