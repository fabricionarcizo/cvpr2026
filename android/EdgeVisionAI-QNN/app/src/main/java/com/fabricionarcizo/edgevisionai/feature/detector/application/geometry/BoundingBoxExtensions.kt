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
package com.fabricionarcizo.edgevisionai.feature.detector.application.geometry

import android.graphics.RectF
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.BoundingBox

/**
 * Extension function to convert a [BoundingBox] to an Android [RectF].
 *
 * @receiver BoundingBox The bounding box to convert.
 *
 * @return RectF The converted RectF object.
 */
fun BoundingBox.toRectF(): RectF = RectF(left, top, right, bottom)

/**
 * Extension function to convert an Android [RectF] to a [BoundingBox].
 *
 * @receiver RectF The RectF to convert.
 *
 * @return BoundingBox The converted BoundingBox object.
 */
fun RectF.toBoundingBox(): BoundingBox =
    BoundingBox(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )
