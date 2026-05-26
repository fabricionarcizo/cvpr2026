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
package com.fabricionarcizo.edgevisionai.feature.detector.presentation.config

import android.graphics.Color

/** Configuration constants for overlay rendering. */
object OverlayConfig {
    /**
     * Default primary text size in pixels.
     */
    const val DEFAULT_PRIMARY_TEXT_SIZE_PX = 36f

    /**
     * Default bounding box stroke width in pixels.
     */
    const val BBOX_STROKE_WIDTH_PX = 4f

    /**
     * Default label padding in pixels.
     */
    const val LABEL_PADDING_PX = 8f

    /**
     * Horizontal offset for label boxes relative to the bounding box's left edge. Positive values
     * move the label to the right, negative values move it to the left.
     */
    const val LABEL_HORIZONTAL_OFFSET_PX = -2f

    /**
     * Default primary text color.
     */
    const val DEFAULT_PRIMARY_TEXT_COLOR = Color.YELLOW

    /**
     * Default background color.
     */
    const val DEFAULT_BACKGROUND_COLOR = Color.GREEN
}
