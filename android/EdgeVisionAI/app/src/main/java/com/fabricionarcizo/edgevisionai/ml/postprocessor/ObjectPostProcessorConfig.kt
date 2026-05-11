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
     * Number of anchor predictions produced by YOLOX-S at 640×640 input
     * (80×80 + 40×40 + 20×20 = 8400).
     */
    const val NUM_DETECTIONS = 8400

    /**
     * Number of values per detection in the `bboxes` output tensor (cx, cy, w, h).
     */
    const val NUM_BBOX_VALUES = 4

    /**
     * Number of values per detection in the `scores` output tensor
     * (1 obj_score + 80 class scores = 81).
     */
    const val NUM_SCORE_VALUES = 81

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
