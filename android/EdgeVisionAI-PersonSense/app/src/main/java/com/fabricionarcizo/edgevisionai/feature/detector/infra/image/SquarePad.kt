package com.fabricionarcizo.edgevisionai.feature.detector.infra.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import javax.inject.Inject

/**
 * Resize-and-letterbox a bitmap to a fixed `edge × edge` square. Used to bring
 * arbitrary camera frames into the model's expected square input space without
 * distorting aspect ratio.
 *
 * Also handles mapping bbox coordinates produced in the padded-square space
 * back to the original frame's pixel space.
 */
class SquarePad @Inject constructor() {

    /**
     * Result of [padToSquare]: the new bitmap plus offsets/sizes needed to
     * un-letterbox model output.
     */
    data class Result(
        val padded: Bitmap,
        val edge: Int,
        val offsetX: Int,
        val offsetY: Int,
        val scaledWidth: Int,
        val scaledHeight: Int,
    )

    /**
     * Resize [src] so its longer edge equals [edge], then centre-pad with
     * black to a square `edge × edge`.
     */
    fun padToSquare(src: Bitmap, edge: Int): Result {
        val srcW = src.width
        val srcH = src.height
        val ratio = edge.toFloat() / maxOf(srcW, srcH)
        val newW = (srcW * ratio).toInt().coerceAtLeast(1)
        val newH = (srcH * ratio).toInt().coerceAtLeast(1)
        val resized = if (newW == srcW && newH == srcH) {
            src
        } else {
            Bitmap.createScaledBitmap(src, newW, newH, true)
        }

        val padded = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        val offsetX = (edge - newW) / 2
        val offsetY = (edge - newH) / 2
        canvas.drawBitmap(resized, offsetX.toFloat(), offsetY.toFloat(), null)
        if (resized !== src) resized.recycle()
        return Result(padded, edge, offsetX, offsetY, newW, newH)
    }

    /**
     * Convert a bbox emitted in the padded-square pixel space back to the
     * original frame's pixel space.
     */
    fun mapBackToOriginal(
        pad: Result,
        originalWidth: Int,
        originalHeight: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): FloatArray {
        val sx1 = (x1 - pad.offsetX).coerceIn(0f, pad.scaledWidth.toFloat())
        val sy1 = (y1 - pad.offsetY).coerceIn(0f, pad.scaledHeight.toFloat())
        val sx2 = (x2 - pad.offsetX).coerceIn(0f, pad.scaledWidth.toFloat())
        val sy2 = (y2 - pad.offsetY).coerceIn(0f, pad.scaledHeight.toFloat())
        val sx = originalWidth.toFloat() / pad.scaledWidth
        val sy = originalHeight.toFloat() / pad.scaledHeight
        return floatArrayOf(sx1 * sx, sy1 * sy, sx2 * sx, sy2 * sy)
    }
}
