package com.fabricionarcizo.edgevisionai.ui.main

import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabricionarcizo.edgevisionai.R
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.CameraStartRequest
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.PersonOverlay
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.CameraPreview
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.FrozenFrame
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.components.AnalyzingOverlay
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.components.CaptureControls
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.components.StatusBar
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.viewmodel.DetectorViewModel

/**
 * Top-level Compose screen for the PersonSense activity. Wires up:
 *  - the CameraX preview + Compose `Canvas` overlay
 *  - the frozen-frame view shown after a capture completes
 *  - the backend picker / status bar / capture controls
 *  - lifecycle plumbing to (un)bind the camera as we come into / out of view
 */
@Composable
fun MainScreen(
    viewModel: DetectorViewModel,
    hasCameraPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

    LaunchedEffect(context) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cameraProvider = future.get() },
            ContextCompat.getMainExecutor(context),
        )
    }

    LaunchedEffect(hasCameraPermission, cameraProvider, previewView, cameraSelector) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        viewModel.startCamera(
            CameraStartRequest(
                lifecycleOwner = lifecycleOwner,
                cameraProvider = provider,
                previewView = view,
                cameraSelector = cameraSelector,
            ),
        )
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopCamera() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Preview stays mounted even when we're showing a frozen frame, so
        // CameraX keeps delivering analyzer frames into LatestFrameStore.
        CameraPreview(
            onPreviewView = { previewView = it },
            modifier = Modifier.fillMaxSize(),
        )

        val frozen = uiState.frozenFrame
        if (frozen != null) {
            FrozenFrame(bitmap = frozen, modifier = Modifier.fillMaxSize())
        }

        PersonOverlay(
            frameWidth = uiState.frameWidth,
            frameHeight = uiState.frameHeight,
            isFrontCamera = uiState.isFrontCamera,
            detections = uiState.detections,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
        ) {
            StatusBar(
                statusText = uiState.statusText,
                subText = uiState.fpsText,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            // CVPR2026 is locked to CPU backend — no picker, no toggle.
            CaptureControls(
                captureEnabled = uiState.captureEnabled && hasCameraPermission,
                captureLabel = if (uiState.frozenFrame != null) {
                    stringResource(R.string.button_capture_again)
                } else {
                    stringResource(R.string.button_capture)
                },
                onCapture = viewModel::onCapture,
                onFlip = {
                    cameraSelector = viewModel.flipCamera()
                },
                flipEnabled = !uiState.isAnalyzing,
            )
        }

        if (uiState.isAnalyzing) {
            AnalyzingOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}
