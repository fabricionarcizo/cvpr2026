// src/main/java/com/jabby/vlm/service/VlmService.kt
package com.jabby.vlm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion

/**
 * Foreground service that runs Qwen3-VL image-description inference.
 *
 * Runs in an isolated `:vlm` process so that memory is separate from
 * the text-only LlmService (`:llm` process).
 *
 * Uses llm-lib's InferenceEngine with the new multimodal extensions:
 *   - loadMmproj()              → loads the vision projector GGUF
 *   - sendUserPromptWithImage() → encodes image via libmtmd + generates tokens
 */
class VlmService : Service() {

    companion object {
        private const val TAG = "VlmService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "vlm_service_channel"

        // Actions for startService commands
        const val ACTION_LOAD_MODELS = "com.jabby.vlm.ACTION_LOAD_MODELS"
        const val ACTION_UNLOAD_MODELS = "com.jabby.vlm.ACTION_UNLOAD_MODELS"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_MMPROJ_PATH = "mmproj_path"
        const val EXTRA_SYSTEM_PROMPT = "system_prompt"
    }

    private var engine: InferenceEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentGenerationJob: Job? = null
    private val generationLock = Any()
    @Volatile private var mmprojLoaded = false
    @Volatile private var storedSystemPrompt: String? = null

    // ══════════════════════════════════════════════════════════════
    // Service Lifecycle
    // ══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VlmService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("VLM Service starting…"))
        initializeEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOAD_MODELS -> {
                val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)
                val mmprojPath = intent.getStringExtra(EXTRA_MMPROJ_PATH)
                val systemPrompt = intent.getStringExtra(EXTRA_SYSTEM_PROMPT)
                if (modelPath != null && mmprojPath != null) {
                    serviceScope.launch {
                        loadModelInternal(modelPath)
                        loadMmprojInternal(mmprojPath)
                        systemPrompt?.let { setSystemPromptInternal(it) }
                    }
                }
            }
            ACTION_UNLOAD_MODELS -> {
                unloadModelInternal()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "Client binding to VlmService")
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "VlmService destroyed")
        synchronized(generationLock) {
            currentGenerationJob?.cancel()
            currentGenerationJob = null
        }
        serviceScope.cancel()
        engine?.cleanUp()
        engine?.destroy()
        mmprojLoaded = false
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════
    // Engine Management
    // ══════════════════════════════════════════════════════════════

    private fun initializeEngine() {
        serviceScope.launch(Dispatchers.Default) {
            try {
                Log.i(TAG, "Initializing VLM inference engine...")
                engine = AiChat.getInferenceEngine(this@VlmService)
                Log.i(TAG, "VLM inference engine initialized")
                updateNotification("VLM Service ready — no model loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize VLM engine", e)
                updateNotification("VLM Service error: ${e.message}")
            }
        }
    }

    private suspend fun loadModelInternal(modelPath: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val eng = engine ?: return@withContext false

                // Wait for engine to finish initializing (handles startup race)
                Log.i(TAG, "Waiting for engine initialization...")
                updateNotification("Initializing engine…")
                eng.state.first { it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing }

                // Auto-recover from error state
                if (eng.state.value is InferenceEngine.State.Error) {
                    Log.i(TAG, "Engine in error state, resetting...")
                    eng.cleanUp()
                }

                Log.i(TAG, "Loading text backbone: $modelPath")
                updateNotification("Loading text model…")
                eng.loadModel(modelPath)
                updateNotification("Text model loaded")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                updateNotification("Failed to load model")
                false
            }
        }
    }

    private suspend fun loadMmprojInternal(mmprojPath: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                Log.i(TAG, "Loading vision projector: $mmprojPath")
                updateNotification("Loading vision projector…")
                engine?.loadMmproj(mmprojPath)
                mmprojLoaded = true
                updateNotification("VLM ready (model + mmproj loaded)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load mmproj", e)
                mmprojLoaded = false
                updateNotification("Failed to load vision projector")
                false
            }
        }
    }

    private suspend fun setSystemPromptInternal(prompt: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                engine?.setSystemPrompt(prompt)
                storedSystemPrompt = prompt
                updateNotification("VLM ready")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set system prompt", e)
                false
            }
        }
    }

    private fun unloadModelInternal() {
        currentGenerationJob?.cancel()
        engine?.cleanUp()
        mmprojLoaded = false
        updateNotification("Model unloaded")
    }

    // ══════════════════════════════════════════════════════════════
    // AIDL Binder Implementation
    // ══════════════════════════════════════════════════════════════

    private val binder = object : IVlmService.Stub() {

        override fun setBackend(backend: String) {
            try {
                engine?.setBackend(backend)
                Log.i(TAG, "backend set to: $backend")
            } catch (t: Throwable) {
                Log.w(TAG, "setBackend failed", t)
            }
        }

        override fun setImageMaxTokens(maxTokens: Int) {
            try {
                engine?.setImageMaxTokens(maxTokens)
                Log.i(TAG, "image_max_tokens set to: $maxTokens")
            } catch (t: Throwable) {
                Log.w(TAG, "setImageMaxTokens failed", t)
            }
        }

        override fun loadModel(modelPath: String): Boolean {
            return runBlocking {
                loadModelInternal(modelPath)
            }
        }

        override fun loadMmproj(mmprojPath: String): Boolean {
            return runBlocking {
                loadMmprojInternal(mmprojPath)
            }
        }

        override fun setSystemPrompt(prompt: String): Boolean {
            return runBlocking {
                setSystemPromptInternal(prompt)
            }
        }

        override fun describeImage(
            imageBytes: ByteArray,
            textPrompt: String,
            maxTokens: Int,
            callback: IVlmCallback?
        ) {
            if (callback == null) {
                Log.w(TAG, "describeImage() called with null callback")
                return
            }

            // Input validation
            if (maxTokens <= 0 || maxTokens > 4096) {
                safeCallback(callback) { it.onError("Invalid maxTokens: $maxTokens (must be 1–4096)") }
                return
            }
            if (imageBytes.isEmpty()) {
                safeCallback(callback) { it.onError("Empty image data") }
                return
            }
            if (imageBytes.size > 20 * 1024 * 1024) {
                safeCallback(callback) { it.onError("Image too large (${imageBytes.size / 1024 / 1024} MB, max 20 MB)") }
                return
            }

            val eng = engine
            if (eng == null) {
                safeCallback(callback) { it.onError("Engine not initialized") }
                return
            }

            if (!mmprojLoaded) {
                safeCallback(callback) { it.onError("Vision projector not loaded. Call loadMmproj() first.") }
                return
            }

            val state = eng.state.value
            if (state !is InferenceEngine.State.ModelReady) {
                safeCallback(callback) {
                    it.onError("Model not ready. Current state: ${state.javaClass.simpleName}")
                }
                return
            }

            updateNotification("Describing image…")

            // Thread-safe job cancellation and launch
            synchronized(generationLock) {
                currentGenerationJob?.cancel()
                currentGenerationJob = serviceScope.launch(Dispatchers.Default) {
                    try {
                        eng.sendUserPromptWithImage(textPrompt, imageBytes, maxTokens)
                            .catch { e ->
                                Log.e(TAG, "Image description error", e)
                                safeCallback(callback) { it.onError(e.message ?: "Unknown error") }
                            }
                            .onCompletion { cause ->
                                if (cause == null) {
                                    safeCallback(callback) { it.onComplete() }
                                }
                                updateNotification("VLM ready")
                            }
                            .collect { token ->
                                safeCallback(callback) { it.onToken(token) }
                            }
                    } catch (e: CancellationException) {
                        Log.i(TAG, "Image description cancelled")
                        safeCallback(callback) { it.onError("Cancelled") }
                        updateNotification("VLM ready")
                    }
                }
            }
        }

        override fun describeImages(
            imageData: ByteArray,
            imageSizes: IntArray,
            textPrompt: String,
            maxTokens: Int,
            callback: IVlmCallback?
        ) {
            if (callback == null) {
                Log.w(TAG, "describeImages() called with null callback")
                return
            }

            if (maxTokens <= 0 || maxTokens > 4096) {
                safeCallback(callback) { it.onError("Invalid maxTokens: $maxTokens (must be 1–4096)") }
                return
            }
            if (imageSizes.isEmpty() || imageData.isEmpty()) {
                safeCallback(callback) { it.onError("Empty image data") }
                return
            }

            // Reconstruct individual images from concatenated data
            val imageArrays = mutableListOf<ByteArray>()
            var offset = 0
            for (size in imageSizes) {
                if (offset + size > imageData.size) {
                    safeCallback(callback) { it.onError("Image data size mismatch") }
                    return
                }
                imageArrays.add(imageData.copyOfRange(offset, offset + size))
                offset += size
            }

            val eng = engine
            if (eng == null) {
                safeCallback(callback) { it.onError("Engine not initialized") }
                return
            }

            if (!mmprojLoaded) {
                safeCallback(callback) { it.onError("Vision projector not loaded. Call loadMmproj() first.") }
                return
            }

            val state = eng.state.value
            if (state !is InferenceEngine.State.ModelReady) {
                safeCallback(callback) {
                    it.onError("Model not ready. Current state: ${state.javaClass.simpleName}")
                }
                return
            }

            updateNotification("Describing ${imageArrays.size} images…")

            synchronized(generationLock) {
                currentGenerationJob?.cancel()
                currentGenerationJob = serviceScope.launch(Dispatchers.Default) {
                    try {
                        // Reset context (clear KV cache) before each multi-image call
                        // so stale context from previous bursts doesn't leak in
                        storedSystemPrompt?.let { prompt ->
                            eng.setSystemPrompt(prompt)
                        }

                        eng.sendUserPromptWithImages(textPrompt, imageArrays.toList(), maxTokens)
                            .catch { e ->
                                Log.e(TAG, "Multi-image description error", e)
                                safeCallback(callback) { it.onError(e.message ?: "Unknown error") }
                            }
                            .onCompletion { cause ->
                                if (cause == null) {
                                    safeCallback(callback) { it.onComplete() }
                                }
                                updateNotification("VLM ready")
                            }
                            .collect { token ->
                                safeCallback(callback) { it.onToken(token) }
                            }
                    } catch (e: CancellationException) {
                        Log.i(TAG, "Multi-image description cancelled")
                        safeCallback(callback) { it.onError("Cancelled") }
                        updateNotification("VLM ready")
                    }
                }
            }
        }

        override fun cancelGeneration() {
            synchronized(generationLock) {
                currentGenerationJob?.cancel()
                currentGenerationJob = null
            }
            updateNotification("VLM ready")
        }

        override fun unloadModel() {
            unloadModelInternal()
        }

        override fun isReady(): Boolean {
            return mmprojLoaded && engine?.state?.value is InferenceEngine.State.ModelReady
        }

        override fun getState(): String {
            val engineState = engine?.state?.value?.javaClass?.simpleName ?: "Null"
            val mmproj = if (mmprojLoaded) "+mmproj" else ""
            return "$engineState$mmproj"
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Utilities
    // ══════════════════════════════════════════════════════════════

    private inline fun safeCallback(
        callback: IVlmCallback,
        action: (IVlmCallback) -> Unit
    ) {
        try {
            action(callback)
        } catch (e: RemoteException) {
            Log.w(TAG, "Client disconnected", e)
        } catch (e: Exception) {
            Log.w(TAG, "Callback delivery failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Qwen3-VL Realtime",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VLM (vision-language model) service status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Qwen3-VL Realtime")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
