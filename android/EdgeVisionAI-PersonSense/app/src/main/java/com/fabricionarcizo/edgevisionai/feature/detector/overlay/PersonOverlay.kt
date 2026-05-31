package com.fabricionarcizo.edgevisionai.feature.detector.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.PersonDetection

/**
 * Compose overlay that draws bounding boxes from [detections] on top of a
 * camera preview / frozen frame.
 *
 * The boxes are emitted by [OverlayCoordinateMapper] in view-pixel coordinates
 * so all this composable does is paint primitives.
 *
 * @param frameWidth source frame width in pixels (0 → don't draw).
 * @param frameHeight source frame height in pixels.
 * @param isFrontCamera flip x-axis when true (front camera mirror).
 */
@Composable
fun PersonOverlay(
    frameWidth: Int,
    frameHeight: Int,
    isFrontCamera: Boolean,
    detections: List<PersonDetection>,
    boxColor: Color = OverlayConfig.DEFAULT_BOX_COLOR,
    labelColor: Color = OverlayConfig.DEFAULT_LABEL_TEXT_COLOR,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { OverlayConfig.BBOX_STROKE_WIDTH_DP.dp.toPx() }
    val labelTextSizePx = with(density) { OverlayConfig.LABEL_TEXT_SIZE_SP.sp.toPx() }
    val labelPaddingPx = with(density) { OverlayConfig.LABEL_PADDING_DP.dp.toPx() }
    val mapper = remember { OverlayCoordinateMapper() }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (frameWidth == 0 || frameHeight == 0 || detections.isEmpty()) return@Canvas

        val viewWidthPx = size.width.toInt()
        val viewHeightPx = size.height.toInt()
        val items = mapper.mapBoxes(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            isFrontCamera = isFrontCamera,
            viewWidth = viewWidthPx,
            viewHeight = viewHeightPx,
            detections = detections,
        )

        // Native canvas for text (Compose lacks a primitive drawText today).
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSizePx
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val backgroundPaint = android.graphics.Paint().apply {
            color = boxColor.toArgb()
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val textBoundsRect = android.graphics.Rect()

        items.forEach { item ->
            val r = item.rect

            // Box stroke.
            drawRect(
                color = boxColor,
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                style = Stroke(width = strokeWidthPx),
            )

            // Label text + pill background.
            val label = item.label
            labelPaint.getTextBounds(label, 0, label.length, textBoundsRect)
            val textWidth = textBoundsRect.width().toFloat()
            val textHeight = textBoundsRect.height().toFloat()
            val textX = r.left + labelPaddingPx
            val textY = r.top - labelPaddingPx

            nativeCanvas.drawRect(
                r.left,
                textY - textHeight - labelPaddingPx,
                textX + textWidth + labelPaddingPx,
                textY + labelPaddingPx,
                backgroundPaint,
            )
            nativeCanvas.drawText(label, textX, textY, labelPaint)
        }
    }
}

