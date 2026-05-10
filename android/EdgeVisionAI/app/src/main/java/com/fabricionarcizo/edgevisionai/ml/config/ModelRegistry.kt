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
 * This object holds constant model configurations used in the application.
 *
 * Each configuration is represented by a [ModelConfig] data class instance.
 *
 * These configurations include details such as the model file name, input dimensions, input and
 * output layer names, and any alternative names for outputs.
 */
object ModelRegistry {
    /**
     * Configuration for the YOLO-NAS-S-INT8 model.
     *
     * Uses direct coordinate prediction without anchor generation.
     */
    val objectDetectorConfig =
        ModelConfig(
            fileName = "LibreYOLOXs_int8_sm7325.dlc",
            inputShape = intArrayOf(1, 3, 640, 640).toList(),
            isNchw = true,
            inputLayerName = "images",
            outputLayerNames = emptyList(),
            outputAlternativeNames = listOf("detections"),
        )
}
