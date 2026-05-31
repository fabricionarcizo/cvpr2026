package com.fabricionarcizo.edgevisionai.ml.postprocessor

import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.BoundingBox
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Parses Qwen-VL bounding-box JSON output into [BoundingBox]es in the model's
 * letterboxed input coordinate space (square `inputEdge` × `inputEdge`).
 *
 * Tolerates:
 *  - extra prose around the JSON
 *  - different key names: `"bbox_2d"`, `"bbox"`, `"box"`
 *  - different coordinate schemes:
 *      - absolute pixels in the model's input space (Qwen3-VL)
 *      - normalised `0..1000` (Qwen2-VL style)
 *      - normalised `0..1`
 *  - bare `[x1,y1,x2,y2]` arrays emitted by smaller Qwen3.5 0.8B models
 *
 * The VLM emits no calibrated confidence, so any `"confidence"`/`"score"`
 * field is ignored. The returned [LabeledBoundingBox.label] defaults to
 * `"person"` when absent.
 */
class QwenBBoxParser @Inject constructor() {

    /**
     * @param text raw model output containing bbox JSON.
     * @param inputEdge edge length (px) of the square the bitmap was padded to
     * before being handed to the model; used both for coordinate scaling and
     * for clamping out-of-range coords.
     * @param defaultLabel fallback label when the model omits one.
     */
    fun parse(
        text: String,
        inputEdge: Int,
        defaultLabel: String = "person",
    ): List<LabeledBoundingBox> {
        val items = extractItems(text)
        val out = mutableListOf<LabeledBoundingBox>()

        for (item in items) {
            val coords = item.optJSONArray("bbox_2d")
                ?: item.optJSONArray("bbox")
                ?: item.optJSONArray("box")
                ?: continue
            if (coords.length() < BBOX_COORD_COUNT) continue
            val x1 = coords.optDouble(0, Double.NaN).toFloat()
            val y1 = coords.optDouble(1, Double.NaN).toFloat()
            val x2 = coords.optDouble(2, Double.NaN).toFloat()
            val y2 = coords.optDouble(3, Double.NaN).toFloat()
            if (x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) continue
            val label = item.optString("label").ifBlank { defaultLabel }
            out += LabeledBoundingBox(label, BoundingBox(x1, y1, x2, y2))
        }

        // Fallback for small models (Qwen3.5 0.8B etc.) that emit bare arrays
        // like "[158,304,868,998]" without the bbox_2d/label wrapper.
        if (out.isEmpty()) {
            out += extractBareCoordArrays(text, defaultLabel)
        }
        return rescaleToInputEdge(out, inputEdge)
    }

    private fun extractBareCoordArrays(
        text: String,
        label: String,
    ): List<LabeledBoundingBox> {
        val out = mutableListOf<LabeledBoundingBox>()
        for (m in BARE_COORDS_REGEX.findAll(text)) {
            val (a, b, c, d) = m.destructured
            val x1 = a.toFloatOrNull() ?: continue
            val y1 = b.toFloatOrNull() ?: continue
            val x2 = c.toFloatOrNull() ?: continue
            val y2 = d.toFloatOrNull() ?: continue
            out += LabeledBoundingBox(label, BoundingBox(x1, y1, x2, y2))
        }
        return out
    }

    /**
     * Walk the response looking for `{...}` objects that contain bbox keys.
     * The bbox_2d value array is itself a JSON `[`, so a single greedy match
     * of the outer array fails; pulling individual objects is more reliable.
     */
    private fun extractItems(text: String): List<JSONObject> {
        val bare = extractBareObjects(text)
        if (bare.isNotEmpty()) return bare
        val arr = extractFirstJsonArray(text) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun extractBareObjects(text: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '{') {
                i++
                continue
            }
            val end = matchBalanced(text, i, '{', '}')
            if (end < 0) break
            val candidate = text.substring(i, end + 1)
            try {
                val obj = JSONObject(candidate)
                if (obj.has("bbox_2d") || obj.has("bbox") || obj.has("box")) out += obj
            } catch (_: Exception) {
                // Skip malformed JSON fragments.
            }
            i = end + 1
        }
        return out
    }

    private fun rescaleToInputEdge(
        boxes: List<LabeledBoundingBox>,
        edge: Int,
    ): List<LabeledBoundingBox> {
        if (boxes.isEmpty()) return boxes
        var maxV = 0f
        boxes.forEach { lb ->
            val b = lb.box
            maxV = maxOf(maxV, b.left, b.top, b.right, b.bottom)
        }
        val scale = when {
            maxV <= NORMALISED_01_THRESHOLD -> edge.toFloat()
            maxV > edge * OVERSHOOT_TOLERANCE -> edge.toFloat() / NORMALISED_1000_DIVISOR
            else -> 1f
        }
        if (scale == 1f) return boxes.map { sanitize(it, edge) }
        return boxes.map { lb ->
            val b = lb.box
            sanitize(
                LabeledBoundingBox(
                    lb.label,
                    BoundingBox(
                        b.left * scale,
                        b.top * scale,
                        b.right * scale,
                        b.bottom * scale,
                    ),
                ),
                edge,
            )
        }
    }

    private fun sanitize(lb: LabeledBoundingBox, edge: Int): LabeledBoundingBox {
        val b = lb.box
        val l = b.left.coerceIn(0f, edge.toFloat())
        val t = b.top.coerceIn(0f, edge.toFloat())
        val r = b.right.coerceIn(0f, edge.toFloat())
        val bot = b.bottom.coerceIn(0f, edge.toFloat())
        val nl = minOf(l, r)
        val nr = maxOf(l, r)
        val nt = minOf(t, bot)
        val nbot = maxOf(t, bot)
        return LabeledBoundingBox(lb.label, BoundingBox(nl, nt, nr, nbot))
    }

    private fun extractFirstJsonArray(text: String): JSONArray? {
        val start = text.indexOf('[')
        if (start < 0) return null
        val end = matchBalanced(text, start, '[', ']')
        if (end < 0) return null
        return try {
            JSONArray(text.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    private fun matchBalanced(
        text: String,
        start: Int,
        open: Char,
        close: Char,
    ): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (c) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /**
     * A bounding box plus a label, in the model's input coordinate space.
     */
    data class LabeledBoundingBox(
        val label: String,
        val box: BoundingBox,
    )

    private companion object {
        const val BBOX_COORD_COUNT = 4
        const val NORMALISED_01_THRESHOLD = 1.0f
        const val NORMALISED_1000_DIVISOR = 1000f
        const val OVERSHOOT_TOLERANCE = 1.05f
        val BARE_COORDS_REGEX = Regex(
            """\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,""" +
                """\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*]""",
        )
    }
}
