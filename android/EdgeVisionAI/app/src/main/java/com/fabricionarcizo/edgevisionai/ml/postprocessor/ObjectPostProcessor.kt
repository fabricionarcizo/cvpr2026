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

import android.graphics.Bitmap
import android.graphics.RectF
import com.fabricionarcizo.edgevisionai.di.ml.ObjectDetection
import com.fabricionarcizo.edgevisionai.ml.api.InferenceEngine
import com.fabricionarcizo.edgevisionai.ml.api.TensorOutputs
import com.fabricionarcizo.edgevisionai.ml.config.ModelConfig
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.CLASS_SCORE_START
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NMS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_CLASSES
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_DETECTIONS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_OUTPUTS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.OBJ_SCORE_INDEX
import com.fabricionarcizo.edgevisionai.ml.postprocessor.common.DetectionUtils
import com.fabricionarcizo.edgevisionai.ml.postprocessor.common.PostProcessor
import com.fabricionarcizo.edgevisionai.ml.results.ObjectResult
import javax.inject.Inject

/**
 * Post-processor for YOLO object detection models.
 *
 * This class implements the PostProcessor interface to handle the specific post-processing steps
 * required for YOLO model outputs, including decoding bounding boxes, class probabilities, and
 * applying Non-Maximum Suppression (NMS).
 *
 * @property config The model configuration containing input/output layer names and shapes.
 * @property classNames The list of class names corresponding to model output indices.
 */
class ObjectPostProcessor
    @Inject
    constructor(
        @param:ObjectDetection private val config: ModelConfig,
        @param:ObjectDetection private val classNames: List<String>,
    ) : PostProcessor<List<ObjectResult>> {
        /**
         * Flat scratch buffer for the single YOLOX output tensor [NUM_DETECTIONS × NUM_OUTPUTS].
         */
        private val outputScratch = FloatArray(NUM_DETECTIONS * NUM_OUTPUTS)

        /**
         * Processes the YOLOX model output to extract detection results.
         *
         * YOLOX produces a single tensor of shape [NUM_DETECTIONS, NUM_OUTPUTS] (e.g. [8400, 85]).
         * Each row contains: cx, cy, w, h, obj_score, class_scores[80].
         * Confidence = obj_score × max(class_scores). Coordinates are in the 640-pixel input space
         * and are unscaled back to original bitmap dimensions using the letterbox ratio.
         *
         * @param engine The inference engine to run the model.
         * @param bitmap The original input image (before letterboxing).
         * @param threshold The confidence threshold to filter results.
         *
         * @return A list of [ObjectResult] containing detected object labels, confidence scores,
         *      and bounding boxes in original bitmap coordinates.
         */
        override fun process(
            engine: InferenceEngine<TensorOutputs>,
            bitmap: Bitmap,
            threshold: Float,
        ): List<ObjectResult> {
            return engine.infer(bitmap) { output ->
                val tensor = output[config.outputAlternativeNames[0]] ?: return@infer emptyList()

                tensor.read(outputScratch, 0, outputScratch.size)

                // Letterbox ratio: min(inputW / bitmapW, inputH / bitmapH).
                // Dividing 640-space coordinates by this ratio maps them back to bitmap space.
                val inputW = config.inputNHWC[2].toFloat()
                val inputH = config.inputNHWC[1].toFloat()
                val ratio = minOf(inputW / bitmap.width, inputH / bitmap.height)
                val invRatio = 1f / ratio

                val origW = bitmap.width.toFloat()
                val origH = bitmap.height.toFloat()

                val results = ArrayList<ObjectResult>(NUM_DETECTIONS)

                for (i in 0 until NUM_DETECTIONS) {
                    val base = i * NUM_OUTPUTS

                    val objScore = outputScratch[base + OBJ_SCORE_INDEX]

                    // Fast pre-filter: skip if objectness alone is below threshold.
                    if (objScore < threshold) continue

                    // Find best class and compute final confidence.
                    var bestClassId = 0
                    var bestClassScore = outputScratch[base + CLASS_SCORE_START]
                    for (j in 1 until NUM_CLASSES) {
                        val s = outputScratch[base + CLASS_SCORE_START + j]
                        if (s > bestClassScore) {
                            bestClassScore = s
                            bestClassId = j
                        }
                    }

                    val confidence = objScore * bestClassScore
                    if (confidence < threshold) continue

                    // Decode center-format box (cx, cy, w, h) → corner-format (x1, y1, x2, y2).
                    val cx = outputScratch[base]
                    val cy = outputScratch[base + 1]
                    val bw = outputScratch[base + 2]
                    val bh = outputScratch[base + 3]

                    // Scale from 640-pixel space back to original bitmap dimensions.
                    val x1 = ((cx - bw * 0.5f) * invRatio).coerceIn(0f, origW)
                    val y1 = ((cy - bh * 0.5f) * invRatio).coerceIn(0f, origH)
                    val x2 = ((cx + bw * 0.5f) * invRatio).coerceIn(0f, origW)
                    val y2 = ((cy + bh * 0.5f) * invRatio).coerceIn(0f, origH)

                    if (x2 <= x1 || y2 <= y1) continue

                    results.add(ObjectResult(classNames[bestClassId], confidence, RectF(x1, y1, x2, y2)))
                }

                DetectionUtils.applyNMS(results.toMutableList(), NMS.iouThreshold)
            } ?: emptyList()
        }
    }
