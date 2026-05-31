package com.fabricionarcizo.edgevisionai.feature.detector.overlay

import androidx.compose.ui.geometry.Rect
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.PersonDetection
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.model.LabeledBox

/**
 * Maps detection coordinates from frame-pixel space to view-pixel space using
 * `fitCenter`-style scaling: preserve aspect ratio, centre the image inside
 * the view, and letterbox the unfilled axis.
 *
 * For boxes to land on the right image regions the displaying canvas must use
 * the same fit logic (Compose `ContentScale.Fit`-ish behaviour).
 */
class OverlayCoordinateMapper {

    fun mapBoxes(
        frameWidth: Int,
        frameHeight: Int,
        isFrontCamera: Boolean,
        viewWidth: Int,
        viewHeight: Int,
        detections: List<PersonDetection>,
    ): List<LabeledBox> {
        if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return emptyList()
        }
        val imageAspect = frameWidth.toFloat() / frameHeight
        val viewAspect = viewWidth.toFloat() / viewHeight

        val scale: Float
        val offsetX: Float
        val offsetY: Float
        if (imageAspect > viewAspect) {
            scale = viewWidth.toFloat() / frameWidth
            offsetX = 0f
            offsetY = (viewHeight - frameHeight * scale) / 2f
        } else {
            scale = viewHeight.toFloat() / frameHeight
            offsetX = (viewWidth - frameWidth * scale) / 2f
            offsetY = 0f
        }

        return detections.map { detection ->
            val box = detection.boundingBox
            val left = if (isFrontCamera) frameWidth - box.right else box.left
            val right = if (isFrontCamera) frameWidth - box.left else box.right
            LabeledBox(
                rect = Rect(
                    left = left * scale + offsetX,
                    top = box.top * scale + offsetY,
                    right = right * scale + offsetX,
                    bottom = box.bottom * scale + offsetY,
                ),
                label = detection.label,
            )
        }
    }
}
