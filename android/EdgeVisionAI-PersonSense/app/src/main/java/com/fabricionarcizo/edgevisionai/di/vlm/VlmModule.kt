package com.fabricionarcizo.edgevisionai.di.vlm

import android.content.Context
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.BootstrapPort
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmDetectorPort
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmServiceConnectionPort
import com.fabricionarcizo.edgevisionai.feature.detector.infra.image.SquarePad
import com.fabricionarcizo.edgevisionai.feature.detector.infra.vlm.BootstrapRepository
import com.fabricionarcizo.edgevisionai.feature.detector.infra.vlm.VlmPersonDetectorAdapter
import com.fabricionarcizo.edgevisionai.feature.detector.infra.vlm.VlmServiceConnectionAdapter
import com.fabricionarcizo.edgevisionai.ml.postprocessor.QwenBBoxParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Singleton-scoped wiring for everything that talks to the VlmService:
 *
 *  - The AIDL connection (one per process).
 *  - The bootstrap handshake repository (stateless but holds model
 *    paths/config).
 *  - The detector adapter.
 *
 * `@Singleton` keeps the connection alive across configuration changes so we
 * don't drop the VlmService bind every time the user rotates.
 */
@Module
@InstallIn(SingletonComponent::class)
object VlmModule {

    @Provides
    @Singleton
    fun provideVlmServiceConnectionPort(
        @ApplicationContext context: Context,
    ): VlmServiceConnectionPort = VlmServiceConnectionAdapter(context)

    @Provides
    @Singleton
    fun provideBootstrapPort(
        @ApplicationContext context: Context,
        connectionPort: VlmServiceConnectionPort,
    ): BootstrapPort = BootstrapRepository(
        context = context,
        connectionPort = connectionPort,
    )

    @Provides
    @Singleton
    fun provideVlmDetectorPort(
        connectionPort: VlmServiceConnectionPort,
        squarePad: SquarePad,
        parser: QwenBBoxParser,
    ): VlmDetectorPort = VlmPersonDetectorAdapter(
        connectionPort = connectionPort,
        squarePad = squarePad,
        parser = parser,
        systemPrompt = "You are a helpful assistant.",
    )
}
