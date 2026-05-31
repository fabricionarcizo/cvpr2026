package com.fabricionarcizo.edgevisionai.feature.detector.domain.model

/**
 * One detected person.
 *
 * The VLM does not emit a calibrated confidence score, so [score] is left
 * optional. Downstream code should treat absence as "unknown confidence".
 *
 * @property boundingBox box in the original frame's pixel space.
 * @property label human-readable label, typically `"person"`.
 * @property score optional confidence in `[0, 1]` if the model provided one.
 */
data class PersonDetection(
    val boundingBox: BoundingBox,
    val label: String = "person",
    val score: Float? = null,
)

/**
 * Aggregate of all person detections for a single frame, plus the inference
 * telemetry the UI surfaces alongside the boxes.
 */
data class DetectorDetections(
    val people: List<PersonDetection> = emptyList(),
    val rawText: String = "",
    val latencyMs: Long = 0L,
    val genMs: Long = 0L,
    val tokenCount: Int = 0,
)
