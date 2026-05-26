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
package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.fabricionarcizo.edgevisionai.feature.detector.application.DetectorController
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorCameraStartRequest
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.state.DetectorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the object detector.
 *
 * @property controller The detector controller managing the detection pipeline.
 */
@HiltViewModel
class DetectorViewModel
    @Inject
    constructor(
        private val controller: DetectorController,
    ) : ViewModel() {
        /**
         * A set of private constants used in this class.
         */
        companion object {
            /**
             * Tag for logging.
             */
            private val TAG = DetectorViewModel::class.qualifiedName
        }

        /**
         * The UI state exposed as a StateFlow.
         */
        private val _uiState = MutableStateFlow(DetectorState())

        /**
         * Publicly exposed UI state as a read-only StateFlow.
         */
        val uiState: StateFlow<DetectorState> = _uiState

        /**
         * Initialization block: loads the model when the ViewModel is created.
         */
        init {
            // Initialize the frame processor to load models.
            controller.initialize(viewModelScope)

            // Start collecting runtime state updates.
            viewModelScope.launch {
                controller.runtimeState.collect { runtime ->
                    _uiState.value = runtime.toUiState()
                }
            }
        }

        /**
         * Starts the camera with the provided request parameters.
         *
         * @param request The request parameters for starting the camera.
         */
        fun startCamera(request: DetectorCameraStartRequest) {
            controller.start(request)
        }

        /**
         * Stops the camera and clears the state buffer.
         */
        fun stopCamera() {
            controller.stop()
        }

        /**
         * Updates the confidence threshold for object detection.
         *
         * @param threshold The new confidence threshold value (between 0.0 and 1.0).
         */
        fun updateObjectThreshold(threshold: Float) {
            controller.updateObjectThreshold(threshold)
        }

        /**
         * Called when the ViewModel is no longer used and will be destroyed.
         */
        override fun onCleared() {
            super.onCleared()
            try {
                controller.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
    }
