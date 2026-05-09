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
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.LARGE_NUM_DETECTIONS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.LARGE_GRID
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.LARGE_STRIDE
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.MEDIUM_NUM_DETECTIONS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.MEDIUM_GRID
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.MEDIUM_STRIDE
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NMS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_ATTRIBUTES
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_CLASSES
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_DETECTIONS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.OBJECTNESS_INDEX
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.SMALL_NUM_DETECTIONS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.SMALL_GRID
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.SMALL_STRIDE
import kotlin.math.exp
import com.fabricionarcizo.edgevisionai.ml.postprocessor.common.DetectionUtils
import com.fabricionarcizo.edgevisionai.ml.postprocessor.common.PostProcessor
import com.fabricionarcizo.edgevisionai.ml.results.ObjectResult
import javax.inject.Inject

/**
 * Post-processor for multi-scale YOLOX object detection models.
 *
 * Reads three output tensors (`output_small`, `output_medium`, `output_large`) each with shape
 * [1, H, W, 85]. Each detection encodes [cx_raw, cy_raw, w_raw, h_raw, obj_logit,
 * cls_logit0..cls_logit79]. The model outputs raw logits — sigmoid is applied in this
 * post-processor for objectness and class scores, and YOLOX anchor-free grid decoding is
 * applied to the bounding box coordinates. The final confidence score is
 * sigmoid(obj_logit) × sigmoid(max_cls_logit). Bounding boxes are decoded from raw offsets to
 * absolute coordinates and rescaled to the original bitmap dimensions before NMS is applied.
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
         * Companion object containing per-detection attribute index constants and helpers.
         */
        companion object {
            /**
             * Index of the raw center-x offset within a detection's attribute vector.
             */
            private const val INDEX_CX = 0

            /**
             * Index of the raw center-y offset within a detection's attribute vector.
             */
            private const val INDEX_CY = 1

            /**
             * Index of the raw log-scale width within a detection's attribute vector.
             */
            private const val INDEX_W = 2

            /**
             * Index of the raw log-scale height within a detection's attribute vector.
             */
            private const val INDEX_H = 3

            /** Applies the sigmoid function to [x], returning a value in (0, 1). */
            private fun sigmoid(x: Float): Float =
                (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
        }

        /**
         * Pre-allocated scratch buffer for the small-scale output head (80×80 cells).
         */
        private val smallScratch = FloatArray(SMALL_NUM_DETECTIONS * NUM_ATTRIBUTES)

        /**
         * Pre-allocated scratch buffer for the medium-scale output head (40×40 cells).
         */
        private val mediumScratch = FloatArray(MEDIUM_NUM_DETECTIONS * NUM_ATTRIBUTES)

        /**
         * Pre-allocated scratch buffer for the large-scale output head (20×20 cells).
         */
        private val largeScratch = FloatArray(LARGE_NUM_DETECTIONS * NUM_ATTRIBUTES)

        /**
         * Temporary instance to avoid repeated allocations during best class search.
         */
        private val tmpBestClass = BestClass()

        /**
         * Data class to hold the best class index and its corresponding score.
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
         * Reads all three scale output tensors, decodes bounding boxes and scores for each
         * detection, filters by the confidence threshold, and applies NMS.
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
                val small = output[config.outputAlternativeNames[0]]
                val medium = output[config.outputAlternativeNames[1]]
                val large = output[config.outputAlternativeNames[2]]

                if (small == null || medium == null || large == null) return@infer emptyList()

                small.read(smallScratch, 0, smallScratch.size)
                medium.read(mediumScratch, 0, mediumScratch.size)
                large.read(largeScratch, 0, largeScratch.size)

                val scaleX = bitmap.width.toFloat() / config.inputNHWC[2]
                val scaleY = bitmap.height.toFloat() / config.inputNHWC[1]

                val results = ArrayList<ObjectResult>(NUM_DETECTIONS)

                collectDetections(smallScratch, SMALL_NUM_DETECTIONS, SMALL_GRID, SMALL_STRIDE, scaleX, scaleY, threshold, results)
                collectDetections(mediumScratch, MEDIUM_NUM_DETECTIONS, MEDIUM_GRID, MEDIUM_STRIDE, scaleX, scaleY, threshold, results)
                collectDetections(largeScratch, LARGE_NUM_DETECTIONS, LARGE_GRID, LARGE_STRIDE, scaleX, scaleY, threshold, results)

                DetectionUtils.applyNMS(results, NMS.iouThreshold)
            } ?: emptyList()
        }

        /**
         * Iterates over all detections in one scale's scratch buffer and appends qualifying
         * detections to [results].
         *
         * Sigmoid is applied to the raw objectness and class logits read from [data] because the
         * model outputs pre-sigmoid values (the INT8 quantization absorbs the sigmoid range,
         * resulting in dequantized values that may be negative). YOLOX anchor-free grid decoding
         * is applied to the bounding box coordinates.
         *
         * @param data The flat scratch buffer for this scale (numDetections × NUM_ATTRIBUTES).
         * @param numDetections The number of grid cells (detections) in this scale.
         * @param gridSize The side length of this scale's grid (80, 40, or 20).
         * @param stride The pixel stride for this scale (8, 16, or 32).
         * @param scaleX Horizontal scale factor from model input space to original bitmap width.
         * @param scaleY Vertical scale factor from model input space to original bitmap height.
         * @param threshold Minimum confidence score (objectness × class score) to keep a detection.
         * @param results Accumulator list to append accepted [ObjectResult]s to.
         */
        private fun collectDetections(
            data: FloatArray,
            numDetections: Int,
            gridSize: Int,
            stride: Int,
            scaleX: Float,
            scaleY: Float,
            threshold: Float,
            results: ArrayList<ObjectResult>,
        ) {
            for (i in 0 until numDetections) {
                val offset = i * NUM_ATTRIBUTES
                val objectness = sigmoid(data[offset + OBJECTNESS_INDEX])

                // Early reject: skip cells with negligible objectness to reduce work.
                if (objectness < 0.01f) continue

                selectBestClass(data, offset, tmpBestClass)
                val score = objectness * tmpBestClass.score

                if (score >= threshold) {
                    val box = decodeBox(data, offset, i, gridSize, stride, scaleX, scaleY)
                    results.add(ObjectResult(classNames[tmpBestClass.id], score, box))
                }
            }
        }

        /**
         * Decodes a raw YOLOX bounding box from [data] at [offset] using anchor-free grid
         * decoding:
         * ```
         *   cx = (raw_cx + grid_x) * stride
         *   cy = (raw_cy + grid_y) * stride
         *   w  = exp(raw_w) * stride
         *   h  = exp(raw_h) * stride
         * ```
         * The resulting center-format box is converted to corner format and scaled to the original
         * bitmap dimensions.
         *
         * @param data The flat scratch buffer containing detection attributes.
         * @param offset The starting index of this detection's attributes in [data].
         * @param cellIndex Linear cell index within this scale's grid (0 to numDetections−1).
         * @param gridSize The side length of this scale's grid (80, 40, or 20).
         * @param stride The pixel stride for this scale (8, 16, or 32).
         * @param scaleX Horizontal scale factor from model input space to original bitmap width.
         * @param scaleY Vertical scale factor from model input space to original bitmap height.
         *
         * @return A [RectF] with coordinates scaled to the original bitmap dimensions.
         */
        private fun decodeBox(
            data: FloatArray,
            offset: Int,
            cellIndex: Int,
            gridSize: Int,
            stride: Int,
            scaleX: Float,
            scaleY: Float,
        ): RectF {
            val gridX = (cellIndex % gridSize).toFloat()
            val gridY = (cellIndex / gridSize).toFloat()

            val cx = (data[offset + INDEX_CX] + gridX) * stride
            val cy = (data[offset + INDEX_CY] + gridY) * stride
            val halfW = exp(data[offset + INDEX_W].toDouble()).toFloat() * stride / 2f
            val halfH = exp(data[offset + INDEX_H].toDouble()).toFloat() * stride / 2f

            return RectF(
                (cx - halfW) * scaleX,
                (cy - halfH) * scaleY,
                (cx + halfW) * scaleX,
                (cy + halfH) * scaleY,
            )
        }

        /**
         * Finds the class with the highest sigmoid-activated score for the detection at [offset]
         * in [data]. Sigmoid is applied to raw class logits before comparison.
         *
         * @param data The flat scratch buffer containing detection attributes.
         * @param offset The starting index of this detection's attributes in [data].
         * @param out An instance of [BestClass] to hold the result.
         */
        private fun selectBestClass(
            data: FloatArray,
            offset: Int,
            out: BestClass,
        ) {
            val base = offset + CLASS_OFFSET

            var best = 0
            var max = sigmoid(data[base])

            for (i in 1 until NUM_CLASSES) {
                val score = sigmoid(data[base + i])
                if (score > max) {
                    max = score
                    best = i
                }
            }

            out.id = best
            out.score = max
        }
    }
