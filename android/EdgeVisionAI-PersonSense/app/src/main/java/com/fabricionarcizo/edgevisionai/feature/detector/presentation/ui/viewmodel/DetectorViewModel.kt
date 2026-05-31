package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabricionarcizo.edgevisionai.feature.detector.application.DetectPersonsUseCase
import com.fabricionarcizo.edgevisionai.feature.detector.application.LoadModelsUseCase
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.Backend
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.BootstrapPort
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmServiceConnectionPort
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.controller.CameraXController
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.controller.LatestFrameStore
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.CameraStartRequest
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.state.DetectorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * Top-level ViewModel for the detector feature.
 *
 * Wires:
 *  - [VlmServiceConnectionPort] for AIDL bind/unbind
 *  - [LoadModelsUseCase] for the bootstrap handshake
 *  - [DetectPersonsUseCase] for the actual describeImage() call
 *  - [CameraXController] + [LatestFrameStore] for the camera pipeline
 *
 * `AndroidViewModel` because we need `Context` for a PowerManager wake lock
 * around the inference call (keeps the kernel from down-clocking the cores
 * mid-generation).
 */
@HiltViewModel
class DetectorViewModel @Inject constructor(
    application: Application,
    private val connectionPort: VlmServiceConnectionPort,
    private val loadModels: LoadModelsUseCase,
    private val detectPersons: DetectPersonsUseCase,
    private val cameraController: CameraXController,
    private val frameStore: LatestFrameStore,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        DetectorUiState(
            modelLabel = loadModels.modelLabel,
            statusText = application.getString(
                com.fabricionarcizo.edgevisionai.R.string.status_loading_model,
            ),
        ),
    )
    val uiState: StateFlow<DetectorUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null
    private val wakeLock: PowerManager.WakeLock? = run {
        val pm = application.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PersonSense:inference")
            ?.apply { setReferenceCounted(false) }
    }

    init {
        connectionPort.bind()
        observeConnection()
    }

    // ── Service lifecycle ───────────────────────────────────────────────

    private fun observeConnection() {
        viewModelScope.launch {
            connectionPort.connected.collect { isConnected ->
                Log.i(TAG, "service connected=$isConnected")
                if (!isConnected) {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = false,
                            captureEnabled = false,
                            statusText = getString(
                                com.fabricionarcizo.edgevisionai.R.string.status_service_disconnected,
                            ),
                        )
                    }
                    return@collect
                }
                if (_uiState.value.isModelLoaded) return@collect
                bootstrapModels(_uiState.value.backend)
            }
        }
    }

    private fun bootstrapModels(backend: Backend) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    statusText = getString(
                        com.fabricionarcizo.edgevisionai.R.string.status_loading_model,
                    ),
                )
            }
            when (val result = loadModels(backend)) {
                BootstrapPort.Result.Ready -> {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = true,
                            captureEnabled = true,
                            statusText = getString(
                                com.fabricionarcizo.edgevisionai.R.string.status_ready,
                            ),
                        )
                    }
                }

                is BootstrapPort.Result.Missing -> {
                    _uiState.update {
                        it.copy(
                            statusText = getString(
                                com.fabricionarcizo.edgevisionai.R.string.status_missing_models,
                                result.needed.joinToString(),
                            ),
                        )
                    }
                }

                is BootstrapPort.Result.Error -> {
                    _uiState.update {
                        it.copy(
                            statusText = getString(
                                com.fabricionarcizo.edgevisionai.R.string.status_load_error,
                                result.message,
                            ),
                        )
                    }
                }
            }
        }
    }

    // ── Backend switching ───────────────────────────────────────────────

    fun selectBackend(backend: Backend) {
        if (backend == _uiState.value.backend) return
        _uiState.update {
            it.copy(
                backend = backend,
                isModelLoaded = false,
                captureEnabled = false,
                statusText = "Switching to ${backend.wireName()}…",
                frozenFrame = null,
                detections = emptyList(),
            )
        }
        try {
            connectionPort.unbind()
        } catch (_: Throwable) {
            // Best-effort.
        }
        connectionPort.bind()
    }

    // ── Camera ───────────────────────────────────────────────────────────

    fun startCamera(request: CameraStartRequest) {
        cameraController.start(request)
        _uiState.update {
            it.copy(
                isFrontCamera = request.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA,
            )
        }
    }

    fun stopCamera() {
        cameraController.stop()
    }

    fun flipCamera(): CameraSelector {
        val next = if (_uiState.value.isFrontCamera) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        backToLive()
        _uiState.update {
            it.copy(isFrontCamera = next == CameraSelector.DEFAULT_FRONT_CAMERA)
        }
        return next
    }

    // ── Capture / inference ─────────────────────────────────────────────

    fun onCapture() {
        if (analyzeJob?.isActive == true) return
        if (!_uiState.value.isModelLoaded) return

        // Tapping while a frozen frame is displayed acts as "back to live".
        if (_uiState.value.frozenFrame != null) {
            backToLive()
            return
        }

        val source = frameStore.snapshotCopy()
        if (source == null) {
            _uiState.update {
                it.copy(
                    statusText = getString(
                        com.fabricionarcizo.edgevisionai.R.string.status_waiting_frame,
                    ),
                )
            }
            return
        }

        analyzeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    captureEnabled = false,
                    isAnalyzing = true,
                    statusText = getString(
                        com.fabricionarcizo.edgevisionai.R.string.status_analyzing,
                    ),
                    detections = emptyList(),
                )
            }
            // Hold a partial wake lock so the kernel doesn't down-clock the
            // CPU mid-inference. Released in finally even if cancelled.
            try {
                wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            } catch (t: Throwable) {
                Log.w(TAG, "wakeLock acquire failed", t)
            }

            val result = try {
                withContext(Dispatchers.IO) { detectPersons(source) }
            } catch (t: Throwable) {
                Log.w(TAG, "detect failed", t)
                null
            } finally {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Throwable) {
                    // Best-effort.
                }
            }

            if (result == null) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        captureEnabled = true,
                        statusText = getString(
                            com.fabricionarcizo.edgevisionai.R.string.status_failed,
                        ),
                    )
                }
                if (!source.isRecycled) source.recycle()
                return@launch
            }

            val tokPerSec = if (result.genMs > 0) {
                result.tokenCount * MS_PER_SEC.toDouble() / result.genMs
            } else {
                0.0
            }

            val statusFormat = if (result.people.size == 1) {
                com.fabricionarcizo.edgevisionai.R.string.result_summary_singular
            } else {
                com.fabricionarcizo.edgevisionai.R.string.result_summary_plural
            }
            val status = getString(
                statusFormat,
                result.people.size,
                result.latencyMs / MS_PER_SEC.toDouble(),
                tokPerSec,
            )
            val fpsText = String.format(
                Locale.US,
                "%d tok / %.1fs gen",
                result.tokenCount,
                result.genMs / MS_PER_SEC.toDouble(),
            )

            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    captureEnabled = true,
                    statusText = status,
                    fpsText = fpsText,
                    detections = result.people,
                    frozenFrame = source,
                    frameWidth = source.width,
                    frameHeight = source.height,
                    // The frozen bitmap is the raw sensor frame; the boxes are
                    // in that pixel space too, so we don't apply front-camera
                    // mirroring on the overlay side either.
                    isFrontCamera = false,
                )
            }
            Log.i(
                TAG,
                "VLM total=${result.latencyMs}ms gen=${result.genMs}ms " +
                    "tokens=${result.tokenCount} boxes=${result.people.size} " +
                    "raw='${result.rawText.take(LOG_RAW_PREFIX_LEN)}'",
            )
        }
    }

    fun backToLive() {
        analyzeJob?.cancel()
        val previous = _uiState.value.frozenFrame
        _uiState.update {
            it.copy(
                frozenFrame = null,
                detections = emptyList(),
                frameWidth = 0,
                frameHeight = 0,
                statusText = if (it.isModelLoaded) {
                    getString(
                        com.fabricionarcizo.edgevisionai.R.string.status_ready,
                        loadModels.modelLabel,
                    )
                } else {
                    it.statusText
                },
            )
        }
        if (previous != null && !previous.isRecycled) previous.recycle()
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        analyzeJob?.cancel()
        cameraController.release()
        try {
            connectionPort.unbind()
        } catch (_: Throwable) {
            // Best-effort.
        }
        val previous = _uiState.value.frozenFrame
        if (previous != null && !previous.isRecycled) previous.recycle()
    }

    /**
     * Whether the OS supports the sustained-performance mode flag — used by
     * the Activity to flip it on while the screen is up. Lives on the VM so
     * the Activity stays dumb.
     */
    val supportsSustainedPerformanceMode: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private fun getString(resId: Int, vararg formatArgs: Any?): String =
        getApplication<Application>().getString(resId, *formatArgs)

    private companion object {
        const val TAG = "DetectorViewModel"
        const val WAKE_LOCK_TIMEOUT_MS = 60_000L
        const val MS_PER_SEC = 1_000L
        const val LOG_RAW_PREFIX_LEN = 120
    }
}
