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
 * Inference engine interface for running ML models.
 */
interface InferenceEngine<Out> {
    /**
     * Initializes the inference engine by loading the model. This method should be called before
     * using the engine for inference. It is safe to call multiple times - subsequent calls will be
     * ignored.
     *
     * @throws IllegalStateException if model loading fails.
     */
    suspend fun initialize()

    /**
     * Run inference and consume the output inside [block]. Engines that return native-backed
     * outputs (e.g., SNPE tensors) should override this to guarantee proper resource release.
     *
     * @param bitmap The input image as a Bitmap.
     * @param block A lambda function that consumes the output.
     *
     * @return The result of the [block] function.
     */
    fun <R> infer(
        bitmap: Bitmap,
        block: (Out) -> R,
    ): R?
}
