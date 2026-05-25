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
package com.fabricionarcizo.edgevisionai.feature.detector.infra.perf

/**
 * Utility class to compute frames per second (FPS) over a sliding time window.
 *
 * This class stores timestamps of recent frame updates and calculates the number of frames within a
 * specified time window (default is 1000 milliseconds).
 *
 * @property windowSizeMs The size of the time window in milliseconds used to compute FPS.
 */
class FPSTracker(
    private val windowSizeMs: Long = 1000L,
) {
    /**
     * Queue of timestamps (in milliseconds) representing the times when frames were recorded.
     *
     * Used internally to calculate how many frames occurred within the defined sliding window
     * ([windowSizeMs]) for FPS estimation.
     */
    private val timestamps = ArrayDeque<Long>()

    /**
     * The most recently computed frames-per-second (FPS) value.
     *
     * This is updated every time [addFrame()] is called.
     */
    var fps = 0.0
        private set

    /**
     * Records the processing of a new frame and updates the FPS estimate.
     *
     * Adds the current timestamp to the internal queue and removes any timestamps that fall outside
     * the sliding time window ([windowSizeMs]). Updates the [fps] value based on the number of
     * frames within the window.
     */
    fun frameProcessed() {
        val nowMs = System.currentTimeMillis()
        timestamps.addLast(nowMs)

        // Remove timestamps older than the window
        while (timestamps.firstOrNull()?.let { nowMs - it > windowSizeMs } == true) {
            timestamps.removeFirst()
        }

        fps = timestamps.size * 1000.0 / windowSizeMs
    }
}
