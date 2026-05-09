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
package com.fabricionarcizo.edgevisionai.ml.postprocessor

import com.fabricionarcizo.edgevisionai.ml.postprocessor.common.NmsConfig

/**
 * Configuration object for YOLO post-processor settings.
 *
 * This object holds configuration parameters specific to the YOLO post-processing, such as
 * Non-Maximum Suppression (NMS) settings.
 */
object ObjectPostProcessorConfig {
    /**
     * Grid size for the small-scale output head (80×80 cells).
     */
    const val SMALL_GRID = 80

    /**
     * Grid size for the medium-scale output head (40×40 cells).
     */
    const val MEDIUM_GRID = 40

    /**
     * Grid size for the large-scale output head (20×20 cells).
     */
    const val LARGE_GRID = 20

    /**
     * Stride (pixels per grid cell) for the small-scale head.
     */
    const val SMALL_STRIDE = 8

    /**
     * Stride (pixels per grid cell) for the medium-scale head.
     */
    const val MEDIUM_STRIDE = 16

    /**
     * Stride (pixels per grid cell) for the large-scale head.
     */
    const val LARGE_STRIDE = 32

    /**
     * Number of detections from the small-scale head.
     */
    const val SMALL_NUM_DETECTIONS = SMALL_GRID * SMALL_GRID

    /**
     * Number of detections from the medium-scale head.
     */
    const val MEDIUM_NUM_DETECTIONS = MEDIUM_GRID * MEDIUM_GRID

    /**
     * Number of detections from the large-scale head.
     */
    const val LARGE_NUM_DETECTIONS = LARGE_GRID * LARGE_GRID

    /**
     * Total number of detections across all three output heads.
     */
    const val NUM_DETECTIONS = SMALL_NUM_DETECTIONS + MEDIUM_NUM_DETECTIONS + LARGE_NUM_DETECTIONS

    /**
     * Number of attributes per detection: 4 bbox + 1 objectness + 80 classes.
     */
    const val NUM_ATTRIBUTES = 85

    /**
     * Index of the objectness score within a detection's attribute vector.
     */
    const val OBJECTNESS_INDEX = 4

    /**
     * Starting index of class scores within a detection's attribute vector.
     */
    const val CLASS_OFFSET = 5

    /**
     * Number of corners in a bounding box.
     */
    const val NUM_CORNERS = 4

    /**
     * Number of classes the model can predict.
     */
    const val NUM_CLASSES = 80

    /**
     * Non-Maximum Suppression (NMS) configuration.
     */
    val NMS = NmsConfig.DEFAULT
}
