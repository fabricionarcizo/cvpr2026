package com.fabricionarcizo.edgevisionai.feature.detector.overlay.model

import androidx.compose.ui.geometry.Rect

/**
 * Drawing primitive used by the overlay layer: a rectangle (in view-pixel
 * coordinates) plus the label string to render above it.
 */
data class LabeledBox(
    val rect: Rect,
    val label: String,
)
