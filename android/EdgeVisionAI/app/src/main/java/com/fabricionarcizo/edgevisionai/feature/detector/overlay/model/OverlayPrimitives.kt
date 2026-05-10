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
package com.fabricionarcizo.edgevisionai.feature.detector.overlay.model

import android.graphics.RectF

/**
 * A box with an associated label to be drawn on an overlay.
 *
 * @property rect The rectangle defining the box's position and size.
 * @property label The text label associated with the box.
 * @property labelAnchor The anchor position for the label relative to the box.
 * @property fill The fill style for the box.
 */
data class LabeledBox(
    val rect: RectF,
    val label: String,
    val labelAnchor: LabelAnchor = LabelAnchor.TopLeft,
    val fill: BoxFill = BoxFill.None,
)

/**
 * Enum representing possible anchor positions for labels on boxes.
 */
enum class LabelAnchor { TopLeft, }

/**
 * Sealed class representing different fill styles for boxes.
 */
sealed class BoxFill {
    /**
     * No fill style.
     */
    data object None : BoxFill()
}
