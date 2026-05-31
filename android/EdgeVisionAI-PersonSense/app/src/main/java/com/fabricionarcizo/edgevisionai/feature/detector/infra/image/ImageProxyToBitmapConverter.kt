package com.fabricionarcizo.edgevisionai.feature.detector.infra.image

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import javax.inject.Inject

/**
 * Converts a CameraX `ImageProxy` (RGBA_8888 plane) into a rotation-corrected
 * `Bitmap`. The result is mutable and owned by the caller — callers are
 * responsible for recycling it.
 */
class ImageProxyToBitmapConverter @Inject constructor() {

    /**
     * @param proxy the analyser frame. Caller MUST call `proxy.close()` itself
     * after this method returns; this class does not own the proxy.
     * @return the bitmap, or null if the proxy had no usable plane.
     */
    fun convert(proxy: ImageProxy): Bitmap? {
        val plane = proxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * proxy.width

        val bitmap = Bitmap.createBitmap(
            proxy.width + rowPadding / pixelStride,
            proxy.height,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(buffer)
        val cropped = if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, proxy.width, proxy.height)
        }
        if (cropped !== bitmap) bitmap.recycle()

        val rotation = proxy.imageInfo.rotationDegrees
        return if (rotation == 0) cropped else rotateBitmap(cropped, rotation)
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out !== src) src.recycle()
        return out
    }
}
