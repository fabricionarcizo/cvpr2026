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
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.CLASS_OFFSET
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NMS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_ATTRIBUTES
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_CLASSES
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_DETECTIONS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.OBJECTNESS_INDEX
import com.fabricionarcizo.edgevisionai.ml.postprocessor.common.DetectionUtils
import com.fabricionarcizo.edgevisionai.ml.postprocessor.common.PostProcessor
import com.fabricionarcizo.edgevisionai.ml.results.ObjectResult
import javax.inject.Inject

/**
 * Post-processor for the LibreYOLOX single-output detection model.
 *
 * Reads the `detections` output tensor of shape [1, 8400, 85] where 8400 = 80×80 + 40×40 + 20×20
 * grid cells from all three FPN scales concatenated. The model fully decodes bounding boxes
 * (grid offset + stride multiply + exp) and applies sigmoid to objectness and class scores
 * internally before export, so this post-processor only needs to:
 *  1. Apply the letterbox inverse transform to convert bbox from 640×640 model space back to
 *     the original camera frame dimensions.
 *  2. Compute confidence = objectness × max(class_score) and filter by threshold.
 *  3. Apply NMS.
 *
 * Attribute layout per cell at `offset = i × 85`:
 *  - [0]: cx — centre-x in model-input pixel space (decoded)
 *  - [1]: cy — centre-y in model-input pixel space (decoded)
 *  - [2]: w  — width in model-input pixel space (decoded)
 *  - [3]: h  — height in model-input pixel space (decoded)
 *  - [4]: sigmoid(objectness) — already in [0, 1]
 *  - [5..84]: sigmoid(class_score) — already in [0, 1]
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
        companion object {
            private const val INDEX_CX = 0
            private const val INDEX_CY = 1
            private const val INDEX_W = 2
            private const val INDEX_H = 3
        }

        /**
         * Pre-allocated scratch buffer for the flat `detections` tensor (8400 × 85 floats).
         */
        private val detectionsScratch = FloatArray(NUM_DETECTIONS * NUM_ATTRIBUTES)

        /**
         * Temporary instance to avoid repeated allocations during best-class search.
         */
        private val tmpBestClass = BestClass()

        /**
         * Holds the winner of a per-cell class search.
         *
         * @param id The index of the best class.
         * @param score The confidence score of the best class.
         */
        private data class BestClass(
            var id: Int = 0,
            var score: Float = 0f,
        )

        /**
         * Processes the model output to extract detection results.
         *
         * Reads the single `detections` tensor, applies the letterbox inverse transform to
         * bounding boxes, filters by confidence threshold, and applies NMS.
         *
         * @param engine The inference engine to run the model.
         * @param bitmap The original input image.
         * @param threshold The confidence threshold to filter results.
         *
         * @return A list of [ObjectResult] containing detected object labels, confidence scores,
         *      and bounding boxes.
         */
        override fun process(
            engine: InferenceEngine<TensorOutputs>,
            bitmap: Bitmap,
            threshold: Float,
        ): List<ObjectResult> {
            return engine.infer(bitmap) { output ->
                val detections = output[config.outputAlternativeNames[0]]
                    ?: return@infer emptyList()

                detections.read(detectionsScratch, 0, detectionsScratch.size)

                // Compute letterbox inverse transform so model-space coordinates map back
                // correctly to the original (non-squished) camera frame.
                val inputW =
                    if (config.isNchw) config.inputShape[3].toFloat()
                    else config.inputShape[2].toFloat()
                val inputH =
                    if (config.isNchw) config.inputShape[2].toFloat()
                    else config.inputShape[1].toFloat()
                val origW = bitmap.width.toFloat()
                val origH = bitmap.height.toFloat()
                val lbScale = minOf(inputW / origW, inputH / origH)
                val padX = (inputW - origW * lbScale) / 2f
                val padY = (inputH - origH * lbScale) / 2f
                val invScale = 1f / lbScale

                val results = ArrayList<ObjectResult>(NUM_DETECTIONS)
                collectDetections(detectionsScratch, padX, padY, invScale, threshold, results)

                DetectionUtils.applyNMS(results, NMS.iouThreshold)
            } ?: emptyList()
        }

        /**
         * Iterates over all 8400 cells in [data] and appends qualifying detections to [results].
         *
         * Objectness and class scores are read directly (already sigmoid-activated by the model)
         * and clamped to [0, 1] to guard against INT8 dequantization artefacts. Bounding boxes
         * are fully decoded by the model — only the letterbox inverse transform is applied here.
         *
         * @param data Flat scratch buffer (NUM_DETECTIONS × NUM_ATTRIBUTES).
         * @param padX Horizontal letterbox padding in model-input pixels.
         * @param padY Vertical letterbox padding in model-input pixels.
         * @param invScale Reciprocal of the letterbox scale factor (original / model).
         * @param threshold Minimum confidence score (objectness × class score) to keep.
         * @param results Accumulator list to append accepted [ObjectResult]s to.
         */
        private fun collectDetections(
            data: FloatArray,
            padX: Float,
            padY: Float,
            invScale: Float,
            threshold: Float,
            results: ArrayList<ObjectResult>,
        ) {
            for (i in 0 until NUM_DETECTIONS) {
                val offset = i * NUM_ATTRIBUTES
                val objectness = data[offset + OBJECTNESS_INDEX].coerceIn(0f, 1f)

                // Early reject: skip cells with negligible objectness to reduce work.
                if (objectness < 0.01f) continue

                selectBestClass(data, offset, tmpBestClass)
                val score = objectness * tmpBestClass.score

                if (score >= threshold) {
                    val box = decodeBox(data, offset, padX, padY, invScale)
                    results.add(ObjectResult(classNames[tmpBestClass.id], score, box))
                }
            }
        }

        /**
         * Converts a pre-decoded bounding box from model-input pixel space to the original
         * bitmap's pixel space by inverting the letterbox transform:
         * ```
         *   x = (cx ± w/2 − padX) × invScale
         *   y = (cy ± h/2 − padY) × invScale
         * ```
         *
         * @param data Flat scratch buffer containing detection attributes.
         * @param offset Starting index of this detection's attributes in [data].
         * @param padX Horizontal letterbox padding in model-input pixels.
         * @param padY Vertical letterbox padding in model-input pixels.
         * @param invScale Reciprocal of the letterbox scale factor (original / model).
         *
         * @return A [RectF] with coordinates in the original bitmap's pixel space.
         */
        private fun decodeBox(
            data: FloatArray,
            offset: Int,
            padX: Float,
            padY: Float,
            invScale: Float,
        ): RectF {
            val cx = data[offset + INDEX_CX]
            val cy = data[offset + INDEX_CY]
            val halfW = data[offset + INDEX_W] / 2f
            val halfH = data[offset + INDEX_H] / 2f

            return RectF(
                (cx - halfW - padX) * invScale,
                (cy - halfH - padY) * invScale,
                (cx + halfW - padX) * invScale,
                (cy + halfH - padY) * invScale,
            )
        }

        /**
         * Finds the class with the highest score for the detection at [offset] in [data].
         * Values are clamped to [0, 1] since the model applies sigmoid before export.
         *
         * @param data Flat scratch buffer containing detection attributes.
         * @param offset Starting index of this detection's attributes in [data].
         * @param out An instance of [BestClass] to hold the result.
         */
        private fun selectBestClass(
            data: FloatArray,
            offset: Int,
            out: BestClass,
        ) {
            val base = offset + CLASS_OFFSET

            var best = 0
            var max = data[base].coerceIn(0f, 1f)

            for (i in 1 until NUM_CLASSES) {
                val score = data[base + i].coerceIn(0f, 1f)
                if (score > max) {
                    max = score
                    best = i
                }
            }

            out.id = best
            out.score = max
        }
    }
