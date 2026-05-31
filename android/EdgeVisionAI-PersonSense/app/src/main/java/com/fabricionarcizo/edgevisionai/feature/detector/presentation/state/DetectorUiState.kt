package com.fabricionarcizo.edgevisionai.feature.detector.presentation.state

import android.graphics.Bitmap
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.Backend
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.PersonDetection

/**
 * UI state for the main detector screen.
 *
 * The view model is the single source of truth: the screen recomposes off
 * `StateFlow<DetectorUiState>`.
 *
 * @property backend selected compute backend.
 * @property modelLabel display name of the currently-loading or loaded model.
 * @property statusText human-readable status line shown at the top.
 * @property isModelLoaded true once the bootstrap handshake finished cleanly.
 * @property isAnalyzing true while a describeImage() call is in flight.
 * @property captureEnabled UI guard for the Capture button.
 * @property frozenFrame post-capture, the bitmap currently displayed instead
 *      of the live preview. Null when in live-preview mode.
 * @property detections boxes to draw on top of [frozenFrame].
 * @property frameWidth/frameHeight pixel size of [frozenFrame].
 * @property fpsText subtitle string (token count / gen time).
 */
data class DetectorUiState(
    val backend: Backend = Backend.CPU,
    val modelLabel: String = "",
    val statusText: String = "",
    val isModelLoaded: Boolean = false,
    val isAnalyzing: Boolean = false,
    val captureEnabled: Boolean = false,
    val frozenFrame: Bitmap? = null,
    val detections: List<PersonDetection> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val isFrontCamera: Boolean = false,
    val fpsText: String = "",
)
