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
package com.fabricionarcizo.edgevisionai.feature.detector.overlay.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.model.LabeledBox
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.config.OverlayConfig

/**
 * Renders object detection overlays with bounding boxes and labels.
 *
 * @property boxPaint The paint used for drawing bounding boxes.
 * @property backgroundPaint The paint used for drawing label backgrounds.
 * @property labelPaint The paint used for drawing label text.
 */
class ObjectOverlayRenderer(
    private val boxPaint: Paint,
    private val backgroundPaint: Paint,
    private val labelPaint: Paint,
) : OverlayRenderer {
    /**
     * Temporary rectangle for measuring text bounds.
     */
    private val textBounds = Rect()

    /**
     * Renders the given labeled boxes onto the provided canvas.
     *
     * @param canvas The canvas to draw on.
     * @param items The list of labeled boxes to render.
     */
    override fun render(
        canvas: Canvas,
        items: List<LabeledBox>,
    ) {
        if (items.isEmpty()) return

        val padding = OverlayConfig.LABEL_PADDING_PX

        items.forEach { item ->
            val rect = item.rect
            canvas.drawRect(rect, boxPaint)

            val label = item.label
            val textX = rect.left + padding + OverlayConfig.LABEL_HORIZONTAL_OFFSET_PX
            val textY = rect.top - padding

            labelPaint.getTextBounds(label, 0, label.length, textBounds)

            canvas.drawRect(
                rect.left + OverlayConfig.LABEL_HORIZONTAL_OFFSET_PX,
                textY + textBounds.top - padding,
                textX + textBounds.width() + padding,
                textY + textBounds.bottom + padding,
                backgroundPaint,
            )
            canvas.drawText(label, textX, textY, labelPaint)
        }
    }
}
