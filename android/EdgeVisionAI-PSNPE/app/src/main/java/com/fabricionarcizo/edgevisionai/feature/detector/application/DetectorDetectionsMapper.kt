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
package com.fabricionarcizo.edgevisionai.feature.detector.application

import com.fabricionarcizo.edgevisionai.feature.detector.application.geometry.toBoundingBox
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.DetectorDetections
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.ObjectDetection
import com.fabricionarcizo.edgevisionai.ml.pipeline.DetectionPipelineResult
import javax.inject.Inject

/**
 * Mapper class to convert [DetectionPipelineResult] to [DetectorDetections].
 */
class DetectorDetectionsMapper
    @Inject
    constructor() {
        /**
         * Maps a [DetectionPipelineResult] to a [DetectorDetections].
         *
         * @param result The [DetectionPipelineResult] to be mapped.
         *
         * @return The mapped [DetectorDetections].
         */
        fun map(result: DetectionPipelineResult): DetectorDetections =
            DetectorDetections(
                objects =
                    result.objectDetections.map {
                        ObjectDetection(
                            boundingBox = it.rect.toBoundingBox(),
                            label = it.label,
                            score = it.score,
                        )
                    },
            )
    }
