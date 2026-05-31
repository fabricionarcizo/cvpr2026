package com.fabricionarcizo.edgevisionai.di.detector

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Empty placeholder module — kept so the detector feature has a stable
 * `@Module @InstallIn(SingletonComponent::class)` namespace to grow into when
 * the application layer needs additional `@Binds` rules. The actual VLM and
 * camera wiring lives in [com.fabricionarcizo.edgevisionai.di.vlm.VlmModule]
 * and the constructor-injected
 * [com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.controller.CameraXController].
 */
@Module
@InstallIn(SingletonComponent::class)
object DetectorBindingsModule
