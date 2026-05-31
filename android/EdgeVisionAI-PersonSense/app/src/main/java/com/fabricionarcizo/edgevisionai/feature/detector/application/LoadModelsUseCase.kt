package com.fabricionarcizo.edgevisionai.feature.detector.application

import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.Backend
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.BootstrapPort
import javax.inject.Inject

/**
 * Use case that drives the VlmService model-load handshake for a given
 * [Backend]. Returns the underlying [BootstrapPort.Result] so the
 * presentation layer can branch on Ready / Missing / Error.
 */
class LoadModelsUseCase @Inject constructor(
    private val bootstrap: BootstrapPort,
) {
    val modelLabel: String get() = bootstrap.modelLabel

    suspend operator fun invoke(backend: Backend): BootstrapPort.Result = bootstrap.load(backend)
}
