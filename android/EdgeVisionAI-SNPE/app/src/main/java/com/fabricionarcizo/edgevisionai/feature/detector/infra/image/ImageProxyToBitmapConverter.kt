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
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * This class converts ImageProxy in RGBA format to Bitmap.
 */
class ImageProxyToBitmapConverter
    @Inject
    constructor() {
        /**
         * Companion object holding constants.
         */
        companion object {
            /**
             * Number of bytes per pixel for ARGB_8888 / RGBA format (4 bytes: A, R, G, B).
             */
            private const val BYTES_PER_PIXEL = 4
        }

        /**
         * Output bitmap reused to avoid allocations.
         */
        private var bitmap: Bitmap? = null

        /**
         * Packed buffer reused to avoid allocations.
         */
        private var packed: ByteBuffer? = null

        /**
         * Converts the given ImageProxy in RGBA format to a Bitmap.
         *
         * @param image ImageProxy in RGBA format to be converted.
         *
         * @return Converted Bitmap.
         */
        fun toBitmap(image: ImageProxy): Bitmap {
            val w = image.width
            val h = image.height
            val plane = image.planes[0]
            val src = plane.buffer
            src.rewind()

            val outBitmap =
                bitmap?.takeIf { it.width == w && it.height == h }
                    ?: createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }

            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            // Fast path: already tightly packed RGBA.
            if (pixelStride == BYTES_PER_PIXEL && rowStride == w * BYTES_PER_PIXEL) {
                outBitmap.copyPixelsFromBuffer(src)
                return outBitmap
            }

            val bufferSize = w * h * BYTES_PER_PIXEL
            val dst =
                packed?.takeIf { it.capacity() == bufferSize }
                    ?: ByteBuffer.allocateDirect(bufferSize).also { packed = it }
            dst.rewind()

            // Copy row-by-row without intermediate ByteArray.
            val rowBytes = w * BYTES_PER_PIXEL
            val originalLimit = src.limit()

            var srcPos = 0
            for (y in 0 until h) {
                src.position(srcPos)
                src.limit(srcPos + rowBytes)
                dst.put(src)
                srcPos += rowStride
            }

            src.limit(originalLimit)
            dst.rewind()
            outBitmap.copyPixelsFromBuffer(dst)
            return outBitmap
        }
    }
