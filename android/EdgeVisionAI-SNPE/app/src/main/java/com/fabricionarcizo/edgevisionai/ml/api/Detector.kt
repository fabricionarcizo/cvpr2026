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
package com.fabricionarcizo.edgevisionai.ml.api

import android.graphics.Bitmap

/**
 * Interface for a generic detector that processes images and returns results of type T.
 */
interface Detector<T> {
    /**
     * Initializes the detector by loading its underlying models. This method should be called
     * before using the detector. It is safe to call multiple times - subsequent calls will be
     * ignored.
     *
     * @throws IllegalStateException if model loading fails.
     */
    suspend fun initialize()

    /**
     * Processes the given bitmap and returns detection results of type T.
     *
     * @param bitmap The input image as a Bitmap.
     * @param threshold The confidence threshold for detections.
     *
     * @return The detection results of type T.
     */
    fun detect(
        bitmap: Bitmap,
        threshold: Float,
    ): T
}
