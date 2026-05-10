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
package com.fabricionarcizo.edgevisionai.ml.detectors

import android.graphics.Bitmap
import com.fabricionarcizo.edgevisionai.di.ml.ObjectDetection
import com.fabricionarcizo.edgevisionai.ml.api.Detector
import com.fabricionarcizo.edgevisionai.ml.api.InferenceEngine
import com.fabricionarcizo.edgevisionai.ml.api.TensorOutputs
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessor
import com.fabricionarcizo.edgevisionai.ml.results.ObjectResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An object detector that uses YOLO model to detect object in images.
 *
 * @param engine The inference engine used to run the YOLO model.
 * @param postProcessor The post-processor to interpret YOLO model outputs.
 */
@Singleton
class ObjectDetector
    @Inject
    constructor(
        @param:ObjectDetection private val engine: InferenceEngine<TensorOutputs>,
        private val postProcessor: ObjectPostProcessor,
    ) : Detector<@JvmSuppressWildcards List<ObjectResult>> {
        /**
         * Initializes the object detector by loading the underlying model. This method should be
         * called before using the detector. It is safe to call multiple times - subsequent calls
         * will be ignored.
         *
         * @throws IllegalStateException if model loading fails.
         */
        override suspend fun initialize() {
            engine.initialize()
        }

        /**
         * Detects objects in the given bitmap image.
         *
         * @param bitmap The input bitmap image for detection.
         * @param threshold The confidence threshold for detections.
         *
         * @return A list of object bounding box detections.
         */
        override fun detect(
            bitmap: Bitmap,
            threshold: Float,
        ): List<ObjectResult> = postProcessor.process(engine, bitmap, threshold)
    }
