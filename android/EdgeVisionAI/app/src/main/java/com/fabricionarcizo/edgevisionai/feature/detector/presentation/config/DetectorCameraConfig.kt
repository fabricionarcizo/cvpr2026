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
package com.fabricionarcizo.edgevisionai.feature.detector.presentation.config

import android.util.Size
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy

/**
 * Configuration data class for setting up the camera used in the detector feature.
 *
 * @param targetResolution The desired resolution for the camera output. Default is 1280x720.
 * @param fallbackRule The rule to apply when the target resolution is not available. Default is to
 *      choose the closest higher resolution.
 * @param aspectRatioStrategy The strategy for selecting the aspect ratio of the camera output.
 *      Default is 16:9 with automatic fallback.
 * @param allowedResolutionMode The mode for selecting allowed resolutions. Default is to prefer
 *      higher resolution over capture rate.
 * @param backpressureStrategy The strategy for handling backpressure in image analysis. Default is
 *      to keep only the latest image.
 * @param depth The maximum number of images to be held in the analysis pipeline. Default is 1.
 * @param outputImageFormat The format of the output images from the camera. Default is RGBA_8888.
 * @param defaultRotation The default rotation for the camera output. Default is 0 degrees.
 */
data class DetectorCameraConfig(
    val targetResolution: Size = Size(1280, 720),
    val fallbackRule: Int = ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER,
    val aspectRatioStrategy: AspectRatioStrategy =
        AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY,
    val allowedResolutionMode: Int =
        ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE,
    val backpressureStrategy: Int = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
    val depth: Int = 1,
    val outputImageFormat: Int = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888,
    val defaultRotation: Int = Surface.ROTATION_0,
) {
    /**
     * Companion object providing a default configuration instance.
     */
    companion object {
        val DEFAULT = DetectorCameraConfig()
    }
}
