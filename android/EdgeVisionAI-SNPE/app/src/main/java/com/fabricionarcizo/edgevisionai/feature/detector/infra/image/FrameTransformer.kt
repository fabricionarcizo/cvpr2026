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
package com.fabricionarcizo.edgevisionai.feature.detector.infra.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import javax.inject.Inject

/**
 * This class applies rotation transformations to Bitmap frames.
 */
class FrameTransformer
    @Inject
    constructor() {
        /**
         * Output bitmap and canvas reused to avoid allocations.
         */
        private var outBitmap: Bitmap? = null

        /**
         * Canvas reused to avoid allocations.
         */
        private var canvas: Canvas? = null

        /**
         * Matrix and Paint reused to avoid allocations.
         */
        private val matrix = Matrix()

        /**
         * Paint with bitmap filtering enabled.
         */
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        /**
         * Last applied rotation angle in degrees.
         */
        private var lastRot: Int = Int.MIN_VALUE

        /**
         * Last source width.
         */
        private var lastSrcW: Int = -1

        /**
         * Last source height.
         */
        private var lastSrcH: Int = -1

        /**
         * Last output width.
         */
        private var lastOutW: Int = -1

        /**
         * Last output height.
         */
        private var lastOutH: Int = -1

        /**
         * Applies rotation transformation to the source bitmap.
         *
         * @param src Source bitmap to be transformed.
         * @param rotationDegrees Rotation angle in degrees (clockwise).
         *
         * @return Transformed bitmap with the specified rotation applied.
         */
        fun transform(
            src: Bitmap,
            rotationDegrees: Int,
        ): Bitmap {
            val rot = ((rotationDegrees % 360) + 360) % 360
            val isVertical = rot == 90 || rot == 270
            val outW = if (isVertical) src.height else src.width
            val outH = if (isVertical) src.width else src.height

            val out =
                outBitmap?.takeIf { it.width == outW && it.height == outH }
                    ?: createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also {
                        outBitmap = it
                        canvas = Canvas(it)
                        lastRot = Int.MIN_VALUE
                    }

            val c = canvas!!

            val dimensionsChanged =
                src.width != lastSrcW ||
                    src.height != lastSrcH ||
                    outW != lastOutW ||
                    outH != lastOutH
            val rotationChanged = rot != lastRot

            if (rotationChanged || dimensionsChanged) {
                matrix.reset()
                matrix.postTranslate(-src.width / 2f, -src.height / 2f)
                matrix.postRotate(rot.toFloat())
                matrix.postTranslate(outW / 2f, outH / 2f)

                lastRot = rot
                lastSrcW = src.width
                lastSrcH = src.height
                lastOutW = outW
                lastOutH = outH
            }

            c.drawBitmap(src, matrix, paint)
            return out
        }
    }
