package com.fabricionarcizo.edgevisionai.feature.detector.domain.ports

import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.Backend

/**
 * Port describing the work needed to take the bound VLM service from "just
 * connected" to "ready to answer describeImage() calls": resolving model files
 * on disk, calling setBackend / setImageMaxTokens / loadModel / loadMmproj /
 * setSystemPrompt in the right order.
 *
 * Implementations live in `infra.vlm` and depend on `IVlmService`.
 */
interface BootstrapPort {
    /**
     * Outcome of [load].
     */
    sealed interface Result {
        /** Models are loaded and the system prompt has been set. */
        data object Ready : Result

        /** One or more model files could not be located on disk. */
        data class Missing(val needed: List<String>) : Result

        /** A wrapped exception from the AIDL load path. */
        data class Error(val message: String) : Result
    }

    /**
     * Identifying label for the loaded model (e.g. "Qwen3-VL 2B (Q8_0)").
     * Surfaced in the status string.
     */
    val modelLabel: String

    /**
     * Load the configured model + mmproj onto [backend], then set the system
     * prompt. Idempotent — returns [Result.Ready] immediately if the service
     * reports it is already loaded.
     */
    suspend fun load(backend: Backend): Result
}
