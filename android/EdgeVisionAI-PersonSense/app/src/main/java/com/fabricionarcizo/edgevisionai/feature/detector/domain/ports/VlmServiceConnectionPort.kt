package com.fabricionarcizo.edgevisionai.feature.detector.domain.ports

import com.jabby.vlm.service.IVlmService
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the AIDL binder lifecycle for the VlmService.
 *
 * Lets the application layer observe connection state and grab the current
 * `IVlmService` without depending on the raw `ServiceConnection` plumbing.
 */
interface VlmServiceConnectionPort {
    /** Whether the bind callback has fired and the binder is live. */
    val connected: StateFlow<Boolean>

    /** Initiate (or re-initiate) the bind. Safe to call multiple times. */
    fun bind()

    /** Unbind. Safe to call when already unbound. */
    fun unbind()

    /** Current `IVlmService`, or null if not connected. */
    fun getService(): IVlmService?
}
