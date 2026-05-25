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
package com.fabricionarcizo.edgevisionai.ml.postprocessor.common

import android.graphics.RectF
import com.fabricionarcizo.edgevisionai.ml.results.ObjectResult
import java.util.Arrays

/**
 * Singleton object providing common utilities for processing detection results,
 * such as Non-Maximum Suppression (NMS).
 */
object DetectionUtils {
    /**
     * Reusable boolean array for tracking suppressed detections.
     */
    private var suppressedArray: BooleanArray? = null

    /**
     * Computes the Intersection-over-Union (IoU) between two bounding boxes.
     *
     * IoU is a metric used to evaluate the overlap between two rectangular regions. It is defined
     * as the area of their intersection divided by the area of their union. A higher IoU indicates
     * a greater overlap.
     *
     * @param a The first bounding box.
     * @param b The second bounding box.
     *
     * @return The IoU score between the two rectangles, ranging from 0.0 to 1.0.
     */
    fun computeIoU(
        a: RectF,
        b: RectF,
    ): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)

        val intersection = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - intersection

        return if (union <= 0f) 0f else intersection / union
    }

    /**
     * Applies Non-Maximum Suppression (NMS) to remove redundant overlapping detections.
     *
     * Detections are first grouped by label. Within each group, they are sorted by confidence, and
     * overlapping boxes (based on Intersection-over-Union) are removed to keep only the most
     * confident prediction per overlapping region.
     *
     * @param detections A list of [ObjectResult]s returned from raw model output.
     * @param iouThreshold The IoU threshold above which overlapping boxes are suppressed.
     *
     * @return A filtered list of [ObjectResult]s with reduced redundancy.
     */
    fun applyNMS(
        detections: MutableList<ObjectResult>,
        iouThreshold: Float,
    ): List<ObjectResult> {
        if (detections.size <= 1) return detections

        detections.sortByDescending { it.score }

        val n = detections.size
        val suppressed = getSuppressed(n)
        Arrays.fill(suppressed, 0, n, false)

        val out = ArrayList<ObjectResult>(n)

        for (i in 0 until n) {
            if (suppressed[i]) continue

            val a = detections[i]
            out.add(a)

            for (j in i + 1 until n) {
                if (suppressed[j]) continue

                val b = detections[j]

                if (a.label != b.label) continue

                if (computeIoU(a.rect, b.rect) >= iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return out
    }

    /**
     * Retrieves a reusable boolean array for tracking suppressed detections. If the existing
     * array is null or smaller than the requested size, a new array of the specified size is
     * created and stored.
     *
     * @param size The required minimum size of the boolean array.
     *
     * @return A boolean array of at least the specified size.
     */
    private fun getSuppressed(size: Int): BooleanArray {
        val cur = suppressedArray
        return if (cur == null || cur.size < size) {
            val next = BooleanArray(size)
            suppressedArray = next
            next
        } else {
            cur
        }
    }
}
