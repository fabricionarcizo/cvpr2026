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

import android.graphics.Bitmap
import android.os.SystemClock
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.FrameAnalyzer
import com.fabricionarcizo.edgevisionai.ml.pipeline.DetectionPipeline
import com.fabricionarcizo.edgevisionai.ml.pipeline.DetectionPipelineResult

/**
 * Default implementation of the FrameProcessor interface that uses a [DetectionPipeline]
 * to process video frames.
 *
 * @property pipeline The detection pipeline used for processing frames.
 * @property detectionsMapper The mapper to convert pipeline results to detector detections.
 */
class FrameProcessor(
    private val pipeline: DetectionPipeline,
    private val detectionsMapper: DetectorDetectionsMapper,
) : FrameAnalyzer {
    /**
     * Initializes the frame processor by initializing the detection pipeline.
     */
    suspend fun initialize() {
        pipeline.initialize()
    }

    /**
     * Updates the confidence threshold used for object detection.
     *
     * @param threshold The new confidence threshold value (between 0.0 and 1.0).
     */
    fun updateObjectThreshold(threshold: Float) {
        pipeline.updateObjectThreshold(threshold)
    }

    /**
     * Companion object used to hold constants.
     */
    companion object {
        /**
         * Constant to convert nanoseconds to milliseconds (10^6).
         */
        private const val NANOS_TO_MILLIS = 1_000_000L
    }

    /**
     * Processes a given bitmap frame using the detection pipeline and returns the result along with
     * timing information.
     *
     * @param bitmap The bitmap frame to be processed.
     *
     * @return The result of processing the frame, including inference and total processing times.
     */
    override fun process(bitmap: Bitmap): FrameProcessResult {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val result: DetectionPipelineResult = pipeline.process(bitmap)

        val t1 = SystemClock.elapsedRealtimeNanos()
        val inferenceMs = (t1 - t0) / NANOS_TO_MILLIS

        // Convert pipeline result to detector detections.
        val detections = detectionsMapper.map(result)

        return FrameProcessResult(
            detections = detections,
            inferenceMs = inferenceMs,
            totalMs = inferenceMs,
        )
    }
}
