package com.fabricionarcizo.edgevisionai.feature.detector.infra.vlm

import android.content.Context
import android.os.Environment
import android.util.Log
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.Backend
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.BootstrapPort
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmServiceConnectionPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton implementation of [BootstrapPort] mirroring the original
 * `VlmBootstrap`. Resolves the Qwen3-VL 2B GGUF + Q8_0 mmproj files from
 * app-private storage (with a Downloads/ fallback), then drives the AIDL
 * load sequence:
 *
 *   setBackend(...) → setImageMaxTokens(72) → loadModel(...) →
 *   loadMmproj(...) → setSystemPrompt(...)
 *
 * The ordering is load-bearing: `setImageMaxTokens` is consumed at mmproj
 * init time and only takes effect when called before [loadMmproj].
 */
class BootstrapRepository(
    private val context: Context,
    private val connectionPort: VlmServiceConnectionPort,
) : BootstrapPort {

    override val modelLabel: String = MODEL_LABEL

    override suspend fun load(backend: Backend): BootstrapPort.Result = withContext(Dispatchers.IO) {
        val service = connectionPort.getService()
            ?: return@withContext BootstrapPort.Result.Error("VlmService not bound")

        if (service.isReady) return@withContext BootstrapPort.Result.Ready

        val modelPath = ensureLocal(MODEL_FILENAME)
            ?: return@withContext BootstrapPort.Result.Missing(listOf(MODEL_FILENAME))
        val mmprojPath = ensureLocal(MMPROJ_FILENAME)
            ?: return@withContext BootstrapPort.Result.Missing(listOf(MMPROJ_FILENAME))

        // VlmService.binder.setBackend() / setImageMaxTokens() silently SWALLOW
        // UnsatisfiedLinkError if the engine's native library hasn't loaded yet.
        // The AIDL caller sees success, but the native global stays zero and
        // mmproj init falls back to the natural ~300 visual tokens (= ~10 s/cap
        // instead of the CVPR2026 max=72 target ~3 s/cap).
        //
        // Poll getState() until the engine is past "Initializing" before we
        // touch any setter. State strings come from InferenceEngine.State.
        var waitAttempts = 0
        while (waitAttempts < ENGINE_READY_ATTEMPTS) {
            val state = try { service.state ?: "" } catch (_: Throwable) { "" }
            if (state.isNotEmpty() && state != "Uninitialized" && state != "Initializing") break
            delay(ENGINE_READY_DELAY_MS)
            waitAttempts++
        }
        if (waitAttempts >= ENGINE_READY_ATTEMPTS) {
            return@withContext BootstrapPort.Result.Error("engine never finished initializing")
        }

        try {
            service.setBackend(backend.wireName())
            // CVPR2026: cap visual tokens at 72 — applied via the AIDL
            // setImageMaxTokens BEFORE loadMmproj() (the cap is read into
            // the mtmd_context_params at init time, not per-request).
            service.setImageMaxTokens(IMAGE_MAX_TOKENS)
            val modelOk = service.loadModel(modelPath)
            if (!modelOk) return@withContext BootstrapPort.Result.Error("loadModel returned false")
            val mmprojOk = service.loadMmproj(mmprojPath)
            if (!mmprojOk) return@withContext BootstrapPort.Result.Error("loadMmproj returned false")
            service.setSystemPrompt(SYSTEM_PROMPT)
            BootstrapPort.Result.Ready
        } catch (t: Throwable) {
            Log.e(TAG, "load failed", t)
            BootstrapPort.Result.Error(t.message ?: t::class.simpleName ?: "unknown")
        }
    }

    /**
     * Locate [filename] in app-private external storage. If it isn't there
     * yet, copy it from the public Downloads/ directory (where the user is
     * expected to drop the GGUFs via adb push or Files app).
     */
    private fun ensureLocal(filename: String): String? {
        val privateDir = context.getExternalFilesDir(null) ?: context.filesDir
        val privateFile = File(privateDir, filename)
        if (privateFile.exists() && privateFile.length() > 0) return privateFile.absolutePath

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val src = File(downloads, filename)
        if (!src.exists() || !src.canRead()) return null

        return try {
            src.inputStream().use { input ->
                privateFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            privateFile.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG, "copy $filename failed", t)
            null
        }
    }

    private companion object {
        const val TAG = "BootstrapRepository"

        // CVPR2026 operating point — apples-to-apples bench-validated config:
        // Qwen3-VL 2B, Q8_0 LM, Q8_0 mmproj, max_image_tokens=72, CPU backend.
        // Measured on S25 (CPU-only): TTFT 2.8 s, mAP@.5 = 0.48.
        const val MODEL_FILENAME = "Qwen3-VL-2B-Q8_0.gguf"
        const val MMPROJ_FILENAME = "mmproj-Qwen3VL-2B-Q8_0.gguf"
        const val MODEL_LABEL = "Qwen3-VL 2B (Q8_0, CVPR2026)"

        // Cliff-edge visual-token cap from the personsense bench. mAP plateaus
        // from 72 → 196 visual tokens, but TTFT drops 5×.
        const val IMAGE_MAX_TOKENS = 72

        // Kept minimal so smaller models aren't overloaded with schema
        // constraints. The actual JSON format instruction lives in the
        // per-request user prompt.
        const val SYSTEM_PROMPT = "You are a helpful assistant."

        // Engine init waits for the native library to finish loading + the
        // backend dlopen to settle. Cold start with OpenCL kernel compilation
        // can take ~12 s, so we give it a generous 60 × 500 ms = 30 s budget.
        const val ENGINE_READY_ATTEMPTS = 60
        const val ENGINE_READY_DELAY_MS = 500L
    }
}
