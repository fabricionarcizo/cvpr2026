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
package com.fabricionarcizo.edgevisionai.ml.preprocess

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * This class provides utility functions for converting a Bitmap to a ByteBuffer and normalizing
 * the pixel values.
 */
class BitmapRgbFloatPreprocessor {
    companion object {
        /**
         * Number of color channels in RGB format.
         */
        private const val RGB_CHANNELS = 3

        /**
         * Number of color channels in RGBA format.
         */
        private const val RGBA_CHANNELS = 4

        /**
         * Mask to convert byte to unsigned int.
         */
        private const val BYTE_MASK = 0xFF

        /**
         * Scale factor for normalizing pixel values to [0, 1].
         */
        private const val NORMALIZE_SCALE = 1 / 255.0f

        /**
         * Threshold to determine if a frame is considered "black".
         */
        private const val BLACK_FRAME_THRESHOLD_PER_PIXEL = 13

        /**
         * Offset for the red channel in the pixel data.
         */
        private const val RED_OFFSET = 0

        /**
         * Offset for the green channel in the pixel data.
         */
        private const val GREEN_OFFSET = 1

        /**
         * Offset for the blue channel in the pixel data.
         */
        private const val BLUE_OFFSET = 2
    }

    /**
     * Temporary buffers for pixel data conversion.
     */
    private var tempFloatBuffer: FloatArray = FloatArray(0)

    /**
     * Temporary ByteBuffer for bitmap pixel data.
     */
    private var tempByteBuffer: ByteBuffer? = null

    /**
     * Flag indicating if the last processed buffer was considered "black".
     */
    private var wasLastBufferBlack: Boolean = false

    /**
     * Checks if the provided ByteBuffer is invalid based on expected size.
     *
     * @param byteBuffer The ByteBuffer to check.
     * @param expectedSize The expected size of the ByteBuffer.
     *
     * @return True if the ByteBuffer is null or its capacity does not match the expected size.
     */
    private fun isBufferInvalid(
        byteBuffer: ByteBuffer?,
        expectedSize: Int,
    ): Boolean = byteBuffer?.capacity() != expectedSize

    /**
     * Indicates whether the last processed buffer was considered "black".
     *
     * @return True if the last buffer was black, false otherwise.
     */
    fun wasLastBufferBlack(): Boolean = wasLastBufferBlack

    /**
     * Converts the input Bitmap to a ByteBuffer and prepares it for normalization.
     *
     * @param inputBitmap The Bitmap to convert.
     *
     * @return The ByteBuffer containing the pixel data of the Bitmap.
     */
    fun convertBitmapToBuffer(inputBitmap: Bitmap) {
        val inputSize = inputBitmap.rowBytes * inputBitmap.height

        if (isBufferInvalid(tempByteBuffer, inputSize)) {
            tempByteBuffer = ByteBuffer.allocate(inputSize)
            tempFloatBuffer = FloatArray(inputBitmap.width * inputBitmap.height * RGB_CHANNELS)
        }

        tempByteBuffer?.apply {
            rewind()
            inputBitmap.copyPixelsToBuffer(this)
        }
    }

    /**
     * Converts the pixel data in the temporary ByteBuffer to a normalized FloatArray in RGB format.
     *
     * @return A FloatArray containing the normalized pixel values in RGB format.
     */
    fun bufferToFloatsRGB(): FloatArray {
        val inputArray = tempByteBuffer?.array() ?: return FloatArray(0)
        val pixelCount = tempFloatBuffer.size / RGB_CHANNELS

        wasLastBufferBlack = sumBlueChannel(pixelCount, inputArray) < (pixelCount * BLACK_FRAME_THRESHOLD_PER_PIXEL)

        return tempFloatBuffer
    }

    /**
     * Converts the pixel data in the temporary ByteBuffer to a normalized FloatArray in NCHW
     * (planar) format: all R values for every pixel, then all G values, then all B values.
     *
     * @return A FloatArray containing the normalized pixel values in CHW planar format.
     */
    fun bufferToFloatsNCHW(): FloatArray {
        val inputArray = tempByteBuffer?.array() ?: return FloatArray(0)
        val pixelCount = tempFloatBuffer.size / RGB_CHANNELS

        wasLastBufferBlack = sumChannelsPlanar(pixelCount, inputArray) < (pixelCount * BLACK_FRAME_THRESHOLD_PER_PIXEL)

        return tempFloatBuffer
    }

    /**
     * Sums the blue channel values from the input pixel array while converting RGBA to normalized
     * RGB.
     *
     * @param pixelCount The number of pixels to process.
     * @param inputArray The input byte array containing pixel data in RGBA format.
     *
     * @return The sum of the blue channel values.
     */
    private fun sumBlueChannel(
        pixelCount: Int,
        inputArray: ByteArray,
    ): Long {
        var blueSum = 0L

        for (i in 0 until pixelCount) {
            val srcIndex = i * RGBA_CHANNELS
            val dstIndex = i * RGB_CHANNELS

            val red = inputArray[srcIndex + RED_OFFSET].toInt() and BYTE_MASK
            val green = inputArray[srcIndex + GREEN_OFFSET].toInt() and BYTE_MASK
            val blue = inputArray[srcIndex + BLUE_OFFSET].toInt() and BYTE_MASK

            tempFloatBuffer[dstIndex] = NORMALIZE_SCALE * red
            tempFloatBuffer[dstIndex + 1] = NORMALIZE_SCALE * green
            tempFloatBuffer[dstIndex + 2] = NORMALIZE_SCALE * blue

            blueSum += blue
        }

        return blueSum
    }

    /**
     * Fills [tempFloatBuffer] in planar CHW order (all R, then all G, then all B) from RGBA input
     * while summing the blue channel to detect black frames.
     *
     * @param pixelCount The number of pixels to process.
     * @param inputArray The input byte array containing pixel data in RGBA format.
     *
     * @return The sum of the blue channel values.
     */
    private fun sumChannelsPlanar(
        pixelCount: Int,
        inputArray: ByteArray,
    ): Long {
        var blueSum = 0L

        for (i in 0 until pixelCount) {
            val srcIndex = i * RGBA_CHANNELS

            val red = inputArray[srcIndex + RED_OFFSET].toInt() and BYTE_MASK
            val green = inputArray[srcIndex + GREEN_OFFSET].toInt() and BYTE_MASK
            val blue = inputArray[srcIndex + BLUE_OFFSET].toInt() and BYTE_MASK

            tempFloatBuffer[i] = NORMALIZE_SCALE * red
            tempFloatBuffer[pixelCount + i] = NORMALIZE_SCALE * green
            tempFloatBuffer[2 * pixelCount + i] = NORMALIZE_SCALE * blue

            blueSum += blue
        }

        return blueSum
    }
}
