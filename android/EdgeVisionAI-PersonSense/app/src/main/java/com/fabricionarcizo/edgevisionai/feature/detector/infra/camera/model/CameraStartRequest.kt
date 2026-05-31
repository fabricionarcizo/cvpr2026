package com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model

import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

/**
 * Parameters needed to (re)bind the CameraX pipeline.
 *
 * @property lifecycleOwner host lifecycle the use cases are bound to.
 * @property cameraProvider [ProcessCameraProvider] obtained from
 *      `ProcessCameraProvider.getInstance(...)`.
 * @property previewView destination Compose-hosted Android view.
 * @property cameraSelector front or back.
 */
data class CameraStartRequest(
    val lifecycleOwner: LifecycleOwner,
    val cameraProvider: ProcessCameraProvider,
    val previewView: PreviewView,
    val cameraSelector: CameraSelector,
)

/**
 * Outcome of a camera start attempt.
 */
sealed interface CameraStartResult {
    /** Camera was bound successfully. */
    data object Started : CameraStartResult

    /** Camera failed to bind. */
    data class Failed(val message: String) : CameraStartResult
}
