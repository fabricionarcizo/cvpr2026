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
package com.fabricionarcizo.edgevisionai.feature.detector.overlay

import android.graphics.RectF
import com.fabricionarcizo.edgevisionai.feature.detector.application.geometry.toRectF
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.ObjectDetection
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.model.LabeledBox
import java.util.Locale

/**
 * Maps detection results' coordinates to overlay view coordinates.
 *
 * @property scoreFormat The format string for confidence scores.
 */
class OverlayCoordinateMapper(
    private val scoreFormat: String = "%.2f",
) {
    /**
     * Maps object detection results to labeled boxes for overlay display using primitive frame
     * params. Avoids allocating a FrameInfo in hot paths (e.g., onDraw).
     */
    fun mapObjects(
        frameWidth: Int,
        frameHeight: Int,
        isFrontCamera: Boolean,
        viewWidth: Int,
        viewHeight: Int,
        objects: List<ObjectDetection>,
    ): List<LabeledBox> {
        val scale = getScaleFactor(frameWidth, frameHeight, viewWidth, viewHeight)
        return objects.map {
            val rect = mapRect(it.boundingBox.toRectF(), isFrontCamera, frameWidth, scale)
            LabeledBox(
                rect = rect,
                label = "${it.label} ${formatScore(it.score)}",
            )
        }
    }

    /**
     * New overload that accepts primitives to avoid needing a FrameInfo allocation at callsite.
     */
    private fun mapRect(
        src: RectF,
        isFrontCamera: Boolean,
        imageWidth: Int,
        scale: Float,
    ): RectF {
        val left = if (isFrontCamera) (imageWidth - src.right) else src.left
        val right = if (isFrontCamera) (imageWidth - src.left) else src.right

        return RectF(
            left * scale,
            src.top * scale,
            right * scale,
            src.bottom * scale,
        )
    }

    /**
     * New getScaleFactor overload that uses primitives to avoid FrameInfo allocation at callsite.
     */
    private fun getScaleFactor(
        frameWidth: Int,
        frameHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
    ): Float {
        val imageAspect = frameWidth.toFloat() / frameHeight
        val viewAspect = viewWidth.toFloat() / viewHeight
        return if (imageAspect > viewAspect) {
            viewHeight.toFloat() / frameHeight
        } else {
            viewWidth.toFloat() / frameWidth
        }
    }

    /**
     * Formats the confidence score using the specified format.
     *
     * @param x The confidence score to format.
     *
     * @return The formatted confidence score as a string.
     */
    private fun formatScore(x: Float): String = String.format(Locale.US, scoreFormat, x)
}
