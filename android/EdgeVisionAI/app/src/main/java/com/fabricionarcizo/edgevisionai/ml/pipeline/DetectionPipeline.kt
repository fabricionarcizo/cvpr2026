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
package com.fabricionarcizo.edgevisionai.ml.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.fabricionarcizo.edgevisionai.di.ml.ObjectDetection
import com.fabricionarcizo.edgevisionai.ml.api.Detector
import com.fabricionarcizo.edgevisionai.ml.api.Pipeline
import com.fabricionarcizo.edgevisionai.ml.results.ObjectResult
import javax.inject.Inject

/**
 * Complete pipeline for object detection.
 *
 * @property objectDetector The object detection model for full-resolution processing.
 * @property config Configuration parameters for the detection pipeline.
 */
class DetectionPipeline
    @Inject
    constructor(
        @param:ObjectDetection private val objectDetector: Detector<@JvmSuppressWildcards List<ObjectResult>>,
        private val config: DetectionPipelineConfig,
    ) : Pipeline<Bitmap, DetectionPipelineResult> {
        /**
         * Configuration constants for the pipeline.
         */
        companion object {
            /**
             * Tag for logging purposes.
             */
            private val TAG = DetectionPipeline::class.qualifiedName
        }

        /**
         * Initializes the pipeline by loading both object detection model. This method should be
         * called before using the pipeline. It is safe to call multiple times - subsequent calls
         * will be ignored.
         *
         * @throws IllegalStateException if model loading fails.
         */
        override suspend fun initialize() {
            Log.d(TAG, "Initializing DetectionPipeline...")
            objectDetector.initialize()
            Log.d(TAG, "Object detector initialized")
            Log.d(TAG, "DetectionPipeline initialization complete")
        }

        /**
         * Processes an input bitmap and returns the detection results.
         *
         * @param input The input bitmap image.
         *
         * @return [DetectionPipelineResult] containing the detected objects.
         */
        override fun process(input: Bitmap): DetectionPipelineResult = processFrame(input)

        /**
         * Updates the confidence threshold used for object detection.
         *
         * @param threshold The new confidence threshold value (between 0.0 and 1.0).
         */
        fun updateObjectThreshold(threshold: Float) {
            config.objectThreshold = threshold
        }

        /**
         * Processes a frame using a full-resolution approach and returns the object detections.
         *
         * @param frame The input frame bitmap.
         *
         * @return [DetectionPipelineResult] containing object detections.
         */
        private fun processFrame(frame: Bitmap): DetectionPipelineResult {
            var finalResult = DetectionPipelineResult(objectDetections = emptyList())

            try {
                val objects = objectDetector.detect(frame, config.objectThreshold)
                finalResult =
                    DetectionPipelineResult(
                        objectDetections = objects,
                    )
            } catch (e: Exception) {
                Log.e(TAG, "Error in processFrame: ", e)
            }

            return finalResult
        }
    }
