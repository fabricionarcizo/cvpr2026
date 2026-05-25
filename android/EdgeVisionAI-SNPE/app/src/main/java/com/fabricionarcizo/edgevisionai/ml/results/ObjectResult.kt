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
package com.fabricionarcizo.edgevisionai.ml.results

import android.graphics.RectF

/**
 * Represents a single object detection result.
 *
 * Each result includes the detected object's label (e.g., "person", "car"), the confidence score
 * from the model, the bounding box in image coordinates, and an optional track ID to identify the
 * detected object across frames.
 *
 * @property label The name or class of the detected object.
 * @property score The model's confidence score for the detection (range: 0.0 to 1.0).
 * @property rect The rectangular area where the object was detected, in image coordinates.
 * @property trackId An optional unique identifier for tracking the object across frames.
 */
data class ObjectResult(
    val label: String,
    val score: Float,
    val rect: RectF,
    val trackId: Long? = null,
)
