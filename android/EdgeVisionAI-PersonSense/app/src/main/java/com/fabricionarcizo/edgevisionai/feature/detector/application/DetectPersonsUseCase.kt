package com.fabricionarcizo.edgevisionai.feature.detector.application

import android.graphics.Bitmap
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.DetectorDetections
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmDetectorPort
import javax.inject.Inject

/**
 * Use case that runs a single person-detection pass on a captured bitmap.
 *
 * Right now this is a thin wrapper over [VlmDetectorPort], but it gives the
 * presentation layer a stable place to depend on as we add features like
 * batching, NMS, or a fall-back classical detector.
 */
class DetectPersonsUseCase @Inject constructor(
    private val detector: VlmDetectorPort,
) {
    suspend operator fun invoke(bitmap: Bitmap): DetectorDetections = detector.detect(bitmap)
}
