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
package com.fabricionarcizo.edgevisionai.feature.detector.application

import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.scopes.ViewModelScoped
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.FrameSource
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.CameraStartResult
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorCameraStartRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Controller responsible for managing the object detector pipeline and detector state flow.
 *
 * This class initializes the frame source and processor, handles starting and stopping the camera,
 * processes object detections, and maintains the detector state. The controller exposes a
 * [StateFlow] for observing the detector state.
 *
 * @property frameSource The source of video frames for detection.
 * @property frameProcessor The processor that runs the detection models on frames.
 */
@ViewModelScoped
class DetectorController
    @Inject
    constructor(
        private val frameSource: FrameSource,
        private val frameProcessor: FrameProcessor,
    ) {
        /**
         * Companion object containing constants for delays and logging.
         */
        companion object {
            /**
             * Tag for logging.
             */
            private val TAG = DetectorController::class.qualifiedName

            /**
             * Interval in milliseconds for updating FPS.
             */
            private const val FPS_UPDATE_INTERVAL_MS = 250L
        }

        /**
         * Internal mutable state flow for the detector state.
         */
        private val _runtimeState = MutableStateFlow(DetectorRuntimeState())

        /**
         * Publicly exposed state flow for the detector state.
         */
        val runtimeState: StateFlow<DetectorRuntimeState> = _runtimeState.asStateFlow()

        /**
         * Timestamp of the last FPS update in milliseconds.
         */
        private var lastFpsUpdateMs = 0L

        /**
         * Mutex to ensure thread-safe initialization and prevent concurrent initialize() calls.
         */
        private val initMutex = Mutex()

        /**
         * Initializes the frame processor and loads the detection models.
         *
         * This method is thread-safe and uses a mutex to ensure only one initialization
         * can proceed at a time, even if called concurrently from multiple threads.
         *
         * @param scope The [CoroutineScope] to launch the initialization coroutine.
         * @param silent If true, suppresses logging output but still updates internal state.
         */
        fun initialize(
            scope: CoroutineScope,
            silent: Boolean = false,
        ) {
            scope.launch {
                initMutex.withLock {
                    // Check if already loading or loaded within the lock to prevent race conditions.
                    val currentState = _runtimeState.value
                    if (currentState.isModelLoading || currentState.isModelLoaded) {
                        return@withLock
                    }

                    // Always update internal loading state for consistency.
                    _runtimeState.update { it.copy(isModelLoading = true, errorMessage = null) }

                    val result =
                        runCatching {
                            withContext(Dispatchers.IO) {
                                if (!silent) {
                                    Log.d(TAG, "Starting pipeline initialization...")
                                }
                                frameProcessor.initialize()
                                if (!silent) {
                                    Log.d(TAG, "Pipeline initialization successful")
                                }
                            }
                        }

                    if (result.isSuccess) {
                        _runtimeState.update { it.copy(isModelLoaded = true, isModelLoading = false) }
                    } else {
                        val ex = result.exceptionOrNull()
                        if (!silent) {
                            Log.e(TAG, "Failed to load models", ex)
                        }
                        _runtimeState.update {
                            it.copy(
                                isModelLoaded = false,
                                isModelLoading = false,
                                errorMessage = "Failed to load object detection models: ${ex?.message}",
                            )
                        }
                    }
                }
            }
        }

        /**
         * Starts the camera with the given [request] and updates the detector state with results.
         *
         * @param request The [DetectorCameraStartRequest] containing camera start parameters.
         *
         * @return The [CameraStartResult] indicating success or failure of the camera start.
         */
        fun start(request: DetectorCameraStartRequest): CameraStartResult {
            val wrapped =
                request.copy(
                    onResults = { payload ->

                        // Update FPS if available.
                        payload.fps?.let { updateFps(it) }

                        // Forward results to original requester.
                        request.onResults(payload)
                    },
                )

            return frameSource.start(wrapped).also { res ->
                if (res is CameraStartResult.Failed) {
                    _runtimeState.update { it.copy(errorMessage = res.message) }
                }
            }
        }

        /**
         * Stops the frame source.
         */
        fun stop() {
            frameSource.stop()
        }

        /**
         * Updates the object confidence threshold used by the detection pipeline.
         *
         * @param threshold The new confidence threshold value (between 0.0 and 1.0).
         */
        fun updateObjectThreshold(threshold: Float) {
            frameProcessor.updateObjectThreshold(threshold)
        }

        /**
         * Updates the FPS in the detector state if the update interval has passed.
         *
         * @param fps The new frames per second value.
         */
        private fun updateFps(fps: Double) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastFpsUpdateMs < FPS_UPDATE_INTERVAL_MS) return
            lastFpsUpdateMs = nowMs
            _runtimeState.update { it.copy(fps = fps) }
        }
    }
