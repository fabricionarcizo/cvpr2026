package com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.controller

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe holder for the most recent camera frame. The camera analyzer
 * keeps overwriting it; the UI (via the ViewModel) reads it on tap.
 *
 * Replacing a frame recycles the previous one to keep memory pressure in
 * check, since the camera produces them faster than the model can consume
 * them and the GC otherwise lags.
 */
@Singleton
class LatestFrameStore @Inject constructor() {
    private val ref = AtomicReference<Bitmap?>()

    /** Atomically replace the held frame, recycling the previous one. */
    fun set(bitmap: Bitmap) {
        val previous = ref.getAndSet(bitmap)
        if (previous != null && previous !== bitmap && !previous.isRecycled) {
            previous.recycle()
        }
    }

    /**
     * Snapshot the current frame as a fresh immutable copy. Returns null when
     * no frame has been received yet. The returned bitmap is owned by the
     * caller.
     */
    fun snapshotCopy(): Bitmap? {
        val current = ref.get() ?: return null
        if (current.isRecycled) return null
        return current.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Drop the held frame and recycle it. Called from onDestroy paths. */
    fun clear() {
        ref.getAndSet(null)?.takeIf { !it.isRecycled }?.recycle()
    }
}
