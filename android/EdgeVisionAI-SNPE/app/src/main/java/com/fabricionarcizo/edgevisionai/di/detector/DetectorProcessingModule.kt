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
package com.fabricionarcizo.edgevisionai.di.detector

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import com.fabricionarcizo.edgevisionai.feature.detector.application.DetectorDetectionsMapper
import com.fabricionarcizo.edgevisionai.feature.detector.application.FrameProcessor
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.FrameAnalyzer
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.FrameSource
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.controller.CameraXCameraController
import com.fabricionarcizo.edgevisionai.feature.detector.infra.image.FrameTransformer
import com.fabricionarcizo.edgevisionai.feature.detector.infra.image.ImageProxyToBitmapConverter
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.config.DetectorCameraConfig
import com.fabricionarcizo.edgevisionai.ml.pipeline.DetectionPipeline

/**
 * Hilt module to provide detector tools dependencies.
 */
@Module
@InstallIn(ViewModelComponent::class)
object DetectorProcessingModule {
    /**
     * Provides an instance of [FrameProcessor].
     *
     * @param pipeline The detection pipeline to be used by the frame processor.
     * @param detectionsMapper The mapper to convert pipeline results to detector detections.
     *
     * @return [FrameProcessor] instance.
     */
    @Provides
    @ViewModelScoped
    fun provideFrameProcessor(
        pipeline: DetectionPipeline,
        detectionsMapper: DetectorDetectionsMapper,
    ): FrameProcessor =
        FrameProcessor(
            pipeline = pipeline,
            detectionsMapper = detectionsMapper,
        )

    /**
     * Provides an instance of [FrameAnalyzer].
     *
     * @param frameProcessor The frame processor to be used as the analyzer.
     *
     * @return [FrameAnalyzer] instance.
     */
    @Provides
    @ViewModelScoped
    fun provideFrameAnalyzer(frameProcessor: FrameProcessor): FrameAnalyzer = frameProcessor

    /**
     * Provides an instance of [FrameSource].
     *
     * @param cameraConfig The configuration for the detector camera.
     * @param imageProxyToBitmapConverter The converter to convert ImageProxy to RGBA Bitmap.
     * @param frameTransformer The transformer to apply transformations to frames.
     * @param frameAnalyzer The analyzer to process frames.
     *
     * @return [FrameSource] instance.
     */
    @Provides
    @ViewModelScoped
    fun provideFrameSource(
        cameraConfig: DetectorCameraConfig,
        imageProxyToBitmapConverter: ImageProxyToBitmapConverter,
        frameTransformer: FrameTransformer,
        frameAnalyzer: FrameAnalyzer,
    ): FrameSource =
        CameraXCameraController(
            cameraConfig = cameraConfig,
            imageProxyToBitmapConverter = imageProxyToBitmapConverter,
            frameTransformer = frameTransformer,
            frameAnalyzer = frameAnalyzer,
        )
}
