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
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NMS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_CLASSES
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_CORNERS
import com.fabricionarcizo.edgevisionai.ml.postprocessor.ObjectPostProcessorConfig.NUM_DETECTIONS
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
         * Companion object containing constants and helper methods for the post-processor.
         */
        companion object {
            /**
             * Index of the x1 coordinate in the bounding box array.
             */
            private const val INDEX_X1 = 0

            /**
             * Index of the y1 coordinate in the bounding box array.
             */
            private const val INDEX_Y1 = 1

            /**
             * Index of the x2 coordinate in the bounding box array.
             */
            private const val INDEX_X2 = 2

            /**
             * Index of the y2 coordinate in the bounding box array.
             */
            private const val INDEX_Y2 = 3

            /**
             * Starting index for class scores in the output array.
             */
            private const val START_CLASS_INDEX = 1
        }

        /**
         * Number of detections output by the model.
         */
        private val boxesScratch = FloatArray(NUM_DETECTIONS * NUM_CORNERS)

        /**
         * Number of classes the model can predict.
         */
        private val classScoresScratch = FloatArray(NUM_DETECTIONS * NUM_CLASSES)

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
         * Temporary instance to avoid repeated allocations during best class search.
         */
        private val tmpBestClass = BestClass()

        /**
         * Processes the model output to extract detection results.
         *
         * This method processes the input bitmap, runs inference, reads output tensors for bounding
         * boxes and class scores, and constructs detection results above the confidence threshold.
         * It also rescales the bounding boxes to match the original bitmap dimensions and applies
         * Non-Maximum Suppression (NMS) to reduce redundant detections.
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
                val boxes = output[config.outputAlternativeNames[0]]
                val classes = output[config.outputAlternativeNames[1]]

                // Check for null outputs.
                if (boxes == null || classes == null) return@infer emptyList()

                boxes.read(boxesScratch, 0, boxesScratch.size)
                classes.read(classScoresScratch, 0, classScoresScratch.size)

                val scaleX = bitmap.width.toFloat() / config.inputNHWC[2]
                val scaleY = bitmap.height.toFloat() / config.inputNHWC[1]

                val results = ArrayList<ObjectResult>(NUM_DETECTIONS)

                for (i in 0 until NUM_DETECTIONS) {
                    val box = decodeBox(i, boxesScratch, scaleX, scaleY)

                    selectBestClass(i, classScoresScratch, tmpBestClass)
                    val bestId = tmpBestClass.id
                    val bestScore = tmpBestClass.score

                    if (box != null && bestScore >= threshold) {
                        val label = classNames[bestId]
                        results.add(ObjectResult(label, bestScore, box))
                    }
                }

                DetectionUtils.applyNMS(results.toMutableList(), NMS.iouThreshold)
            } ?: emptyList()
        }

        /**
         * Creates and scales a RectF from a 4-element sequence within a FloatArray.
         *
         * @param detectionIndex The index of the detection to process.
         * @param boxArray The source array containing bounding box coordinates (y1, x1, y2, x2 or
         *      similar).
         * @param scaleX The scaling factor for x-coordinates.
         * @param scaleY The scaling factor for y-coordinates.
         *
         * @return A scaled RectF, or null if the array access would be out of bounds.
         */
        private fun decodeBox(
            detectionIndex: Int,
            boxArray: FloatArray,
            scaleX: Float,
            scaleY: Float,
        ): RectF? {
            // Calculate the starting index for the bounding box coordinates.
            val boxOffset = detectionIndex * NUM_CORNERS

            // Safety check: Ensure there are 4 elements available starting from boxOffset.
            if (boxOffset < 0 || boxOffset + INDEX_Y2 >= boxArray.size) {
                return null
            }

            val x1 = boxArray[boxOffset + INDEX_X1] * scaleX
            val y1 = boxArray[boxOffset + INDEX_Y1] * scaleY
            val x2 = boxArray[boxOffset + INDEX_X2] * scaleX
            val y2 = boxArray[boxOffset + INDEX_Y2] * scaleY

            return RectF(x1, y1, x2, y2)
        }

        /**
         * Selects the class with the highest score for a given detection.
         *
         * @param detectionIndex The index of the detection to process.
         * @param data The array containing class scores for all detections.
         * @param out An instance of [BestClass] to hold the result.
         */
        private fun selectBestClass(
            detectionIndex: Int,
            data: FloatArray,
            out: BestClass,
        ) {
            val offset = detectionIndex * NUM_CLASSES

            var best = 0
            var max = data[offset]

            var i = START_CLASS_INDEX
            var idx = offset + START_CLASS_INDEX
            while (i < NUM_CLASSES) {
                val score = data[idx]
                if (score > max) {
                    max = score
                    best = i
                }
                i++
                idx++
            }

            out.id = best
            out.score = max
        }
    }
