package com.fabricionarcizo.edgevisionai.feature.detector.domain.model

/**
 * Axis-aligned rectangle in pixel space.
 *
 * Coordinates are always in the source-frame coordinate system (i.e. the original
 * captured bitmap), not in the model's letterboxed input space.
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
