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
package com.fabricionarcizo.edgevisionai.ml.config

/**
 * Represents a single model configuration.
 *
 * Each model includes the filename path, the input tensor shape, the input layer name,
 * the single or multiple output names, and the alternative output names.
 *
 * @property fileName The filename path of the model.
 * @property inputShape The input tensor dimensions as expected by the SNPE runtime.
 *      NCHW models use [N, C, H, W]; NHWC models use [N, H, W, C].
 * @property isNchw True when the model expects NCHW (planar) input; false for NHWC (interleaved).
 * @property inputLayerName The name of the model's input layer.
 * @property outputLayerNames The names of the model's output layers.
 * @property outputAlternativeNames Alternative names for the model's output layers.
 */
data class ModelConfig(
    val fileName: String,
    val inputShape: List<Int>,
    val isNchw: Boolean = false,
    val inputLayerName: String,
    val outputLayerNames: List<String>,
    val outputAlternativeNames: List<String>,
)
