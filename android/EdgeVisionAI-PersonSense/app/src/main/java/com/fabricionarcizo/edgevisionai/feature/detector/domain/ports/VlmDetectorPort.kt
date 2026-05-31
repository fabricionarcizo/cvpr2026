package com.fabricionarcizo.edgevisionai.feature.detector.domain.ports

import android.graphics.Bitmap
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.DetectorDetections

/**
 * Port abstracting the VLM-driven person detector.
 *
 * The infra adapter wraps the AIDL `IVlmService.describeImage()` call, takes
 * care of JPEG encoding + square-letterbox + bbox parsing, and returns boxes
 * already mapped back to the original frame's pixel space.
 */
interface VlmDetectorPort {
    /**
     * Run a single person-detection pass on the supplied bitmap.
     *
     * Must be called from a coroutine; the AIDL call is wrapped in a
     * `suspendCancellableCoroutine`.
     */
    suspend fun detect(bitmap: Bitmap): DetectorDetections
}
