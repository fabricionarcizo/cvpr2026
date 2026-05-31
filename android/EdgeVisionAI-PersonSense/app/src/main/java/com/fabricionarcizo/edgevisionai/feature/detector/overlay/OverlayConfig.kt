package com.fabricionarcizo.edgevisionai.feature.detector.overlay

import androidx.compose.ui.graphics.Color

/**
 * Hard-coded look-and-feel constants for the bbox overlay.
 *
 * Keep these in sync with the reference SNPE app so the screenshots look
 * consistent across the cvpr2026-tutorial repos.
 */
object OverlayConfig {
    /** Stroke width of the bounding-box rectangle in dp. */
    const val BBOX_STROKE_WIDTH_DP = 4f

    /** Padding between the label text and its background pill, in dp. */
    const val LABEL_PADDING_DP = 4f

    /** Default text size for the label in sp. */
    const val LABEL_TEXT_SIZE_SP = 16f

    /** Default colour of the box stroke / label pill background. */
    val DEFAULT_BOX_COLOR = Color(0xFF00C853) // Material green A700.

    /** Default colour of the label text. */
    val DEFAULT_LABEL_TEXT_COLOR = Color(0xFFFFEB3B) // Material yellow.
}
