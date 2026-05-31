package com.fabricionarcizo.edgevisionai.feature.detector.domain.model

/**
 * Compute backend the underlying llama.cpp engine should run on.
 *
 * The strings on the wire (passed to `IVlmService.setBackend`) are the
 * lowercase enum names.
 */
enum class Backend {
    CPU,
    GPU,
    HTP,
    ;

    fun wireName(): String = name.lowercase()
}
