package com.fabricionarcizo.edgevisionai.feature.detector.infra.vlm

import android.graphics.Bitmap
import android.util.Log
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.BoundingBox
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.DetectorDetections
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.PersonDetection
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmDetectorPort
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmServiceConnectionPort
import com.fabricionarcizo.edgevisionai.feature.detector.infra.image.SquarePad
import com.fabricionarcizo.edgevisionai.ml.postprocessor.QwenBBoxParser
import com.jabby.vlm.service.IVlmCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Adapter that turns a `Bitmap` into a list of person detections by:
 *
 *  1. Letterbox-padding the bitmap to `inputEdge × inputEdge`.
 *  2. JPEG-encoding the padded bitmap.
 *  3. Calling `IVlmService.describeImage()` and collecting streamed tokens.
 *  4. Parsing the raw text with [QwenBBoxParser].
 *  5. Mapping the parsed coords back to the original frame's pixel space.
 *
 * Errors from the AIDL side are swallowed and surfaced as empty detection
 * lists with `latencyMs` set — same behaviour as the original code.
 */
class VlmPersonDetectorAdapter(
    private val connectionPort: VlmServiceConnectionPort,
    private val squarePad: SquarePad,
    private val parser: QwenBBoxParser,
    private val systemPrompt: String,
    private val inputEdge: Int = INPUT_EDGE_DEFAULT,
) : VlmDetectorPort {

    override suspend fun detect(bitmap: Bitmap): DetectorDetections {
        val startMs = System.currentTimeMillis()
        val service = connectionPort.getService()
            ?: return DetectorDetections(latencyMs = System.currentTimeMillis() - startMs)

        // Re-send system prompt before each call — without it, the KV cache pos
        // keeps growing and performance degrades until context overflow.
        try {
            service.setSystemPrompt(systemPrompt)
        } catch (_: Throwable) {
            // Best-effort.
        }

        val pad = squarePad.padToSquare(bitmap, inputEdge)
        val jpeg = ByteArrayOutputStream().use { out ->
            pad.padded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        pad.padded.recycle()

        val invocation = invokeService(service, jpeg)
        val parsed = parser.parse(invocation.text, inputEdge)

        val people = parsed.map { lb ->
            val coords = squarePad.mapBackToOriginal(
                pad,
                originalWidth = bitmap.width,
                originalHeight = bitmap.height,
                x1 = lb.box.left,
                y1 = lb.box.top,
                x2 = lb.box.right,
                y2 = lb.box.bottom,
            )
            PersonDetection(
                boundingBox = BoundingBox(coords[0], coords[1], coords[2], coords[3]),
                label = lb.label,
            )
        }

        return DetectorDetections(
            people = people,
            rawText = invocation.text,
            latencyMs = System.currentTimeMillis() - startMs,
            tokenCount = invocation.tokenCount,
            genMs = invocation.genMs,
        )
    }

    private data class Invocation(
        val text: String,
        val tokenCount: Int,
        val genMs: Long,
    )

    private suspend fun invokeService(
        service: com.jabby.vlm.service.IVlmService,
        jpeg: ByteArray,
    ): Invocation = suspendCancellableCoroutine { cont ->
        val acc = StringBuilder()
        var tokenCount = 0
        var firstTokenAt = 0L
        val cb = object : IVlmCallback.Stub() {
            override fun onToken(token: String?) {
                if (token != null) {
                    if (firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                    if (token.isNotEmpty()) tokenCount++
                    acc.append(token)
                }
            }

            override fun onComplete() {
                if (cont.isActive) {
                    val gen = if (firstTokenAt == 0L) 0L else System.currentTimeMillis() - firstTokenAt
                    cont.resume(Invocation(acc.toString(), tokenCount, gen))
                }
            }

            override fun onError(message: String?) {
                if (cont.isActive) {
                    Log.w(TAG, "VLM error: $message")
                    cont.resume(Invocation("", 0, 0))
                }
            }
        }

        try {
            service.describeImage(jpeg, PROMPT, MAX_TOKENS, cb)
        } catch (t: Throwable) {
            Log.w(TAG, "describeImage threw", t)
            if (cont.isActive) cont.resume(Invocation("", 0, 0))
        }

        cont.invokeOnCancellation {
            try {
                service.cancelGeneration()
            } catch (_: Throwable) {
                // Best-effort cancellation.
            }
        }
    }

    companion object {
        const val INPUT_EDGE_DEFAULT = 512

        // Simple-prompt form proven on the 0.8B and 2B Qwen models.
        // QwenBBoxParser tolerates both wrapped objects and bare arrays.
        const val PROMPT = "Outline the position of every person in this image and output " +
            "the bounding box coordinates in JSON format."

        private const val TAG = "VlmPersonDetector"
        private const val MAX_TOKENS = 256
        private const val JPEG_QUALITY = 75
    }
}
