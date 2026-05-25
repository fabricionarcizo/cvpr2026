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
package com.fabricionarcizo.edgevisionai.feature.detector.overlay.paints

import android.graphics.Paint
import androidx.annotation.ColorInt
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.config.OverlayConfig

/**
 * Factory class to create OverlayPaints with colors sourced from resources or theme.
 *
 * @property primaryColor The primary color for bounding boxes and backgrounds.
 * @property labelTextColor The color for label text.
 * @property labelTextSizePx The text size for labels in pixels.
 */
class OverlayPaintFactory(
    @param:ColorInt private val primaryColor: Int,
    @param:ColorInt private val labelTextColor: Int,
    private val labelTextSizePx: Float,
) {
    /**
     * Creates and returns an OverlayPaints instance with configured Paint objects.
     *
     * @return An instance of OverlayPaints containing all necessary Paint objects.
     */
    fun create(): OverlayPaints {
        val objectBoxPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                style = Paint.Style.STROKE
                strokeWidth = OverlayConfig.BBOX_STROKE_WIDTH_PX
            }

        val objectBackgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                style = Paint.Style.FILL
            }

        val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelTextColor
                textSize = labelTextSizePx
                style = Paint.Style.FILL
            }

        return OverlayPaints(
            objectBoxPaint = objectBoxPaint,
            objectBackgroundPaint = objectBackgroundPaint,
            labelPaint = labelPaint,
        )
    }
}
