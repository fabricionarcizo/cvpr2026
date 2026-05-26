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

import android.content.Context
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logs per-second performance metrics to a CSV file.
 *
 * When [start] is called the logger opens a new CSV file and begins collecting per-frame samples
 * supplied via [onFrame]. Every [LOG_INTERVAL_MS] milliseconds it drains the sample buffer,
 * averages the frame-level metrics, reads system-level metrics (CPU, GPU, DSP, memory), and
 * appends a single row to the file.
 *
 * Call [stop] to flush and close the file. The logger is safe to start and stop multiple times.
 *
 * ### CPU strategy (two-tier)
 * 1. **System CPU** — reads the aggregate "cpu" line from `/proc/stat` and computes a delta-based
 *    utilisation percentage. Works on `userdebug`/`eng` builds and permissive-SELinux devices.
 * 2. **App CPU fallback** — if `/proc/stat` is inaccessible (SELinux enforcement on `user` builds),
 *    falls back to `/proc/self/stat` (always readable by the owning process) and reports the
 *    app-process CPU utilisation across all available cores.
 *
 * ### GPU strategy (Qualcomm kgsl via devfreq)
 * Dynamically scans `/sys/class/devfreq/` for an entry containing `"kgsl-3d0"` and reads its
 * `load` file, which provides a direct integer utilisation percentage (0–100). No delta
 * arithmetic is required. Falls back to `0.0` when the devfreq interface is absent.
 *
 * ### DSP strategy
 * This device exposes no CDSP devfreq entry and `/sys/kernel/debug/fastrpc` is absent.
 * `dsp_pct` is always `0.0`.
 *
 * @property context Application context used to resolve the output directory and read memory info.
 * @property appTag Short identifier embedded in the CSV filename (e.g. "PSNPE", "QNN", "SNPE").
 */
class PerformanceLogger(
    private val context: Context,
    private val appTag: String,
) {
    /**
     * Snapshot of per-frame timing and throughput data captured by [onFrame].
     */
    private data class FrameSample(
        val fps: Double,
        val inferenceMs: Long,
        val totalMs: Long,
        val convertMs: Long,
        val transformMs: Long,
    )

    /**
     * Companion object holding constants.
     */
    companion object {
        private val TAG = PerformanceLogger::class.qualifiedName

        /**
         * Interval between CSV rows, in milliseconds.
         */
        private const val LOG_INTERVAL_MS = 1_000L

        /**
         * Assumed Linux kernel tick rate (USER_HZ). Used to convert jiffies to seconds.
         * This is 100 on virtually all Android/Linux devices.
         */
        private const val USER_HZ = 100L

        /**
         * CSV header written once at the top of each log file.
         */
        private const val CSV_HEADER =
            "timestamp,cpu_pct,gpu_pct,dsp_pct,memory_mb,fps,inference_ms,total_ms,convert_ms,transform_ms"

        /**
         * Ordered list of sysfs paths to try for Qualcomm CDSP (Hexagon DSP) load.
         *
         * Each file returns an integer in the range 0–100 when accessible.
         * Paths vary by Snapdragon SoC generation and kernel version.
         */
        private val CDSP_LOAD_PATHS = listOf(
            "/sys/class/devfreq/soc:cdsp/load",
            "/sys/class/devfreq/soc:qcom,cdsp/load",
            "/sys/class/devfreq/4980000.qcom,cdsp/load",
            "/sys/class/devfreq/4a80000.qcom,cdsp/load",
            "/sys/class/devfreq/ac00000.qcom,cdsp/load",
            "/sys/class/devfreq/soc:qcom,cdsp-cdsp-l3-lat/load",
            "/sys/class/devfreq/soc:qcom,cdspss-l3-lat/load",
        )
    }

    private val isRunning = AtomicBoolean(false)
    private val frameBuffer = ConcurrentLinkedQueue<FrameSample>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logJob: Job? = null
    private var writer: PrintWriter? = null

    // System-wide CPU delta state (@Volatile for cross-thread visibility).
    @Volatile private var lastSysCpuTotal = 0L
    @Volatile private var lastSysCpuIdle = 0L

    // App-process CPU delta state (fallback, @Volatile for cross-thread visibility).
    @Volatile private var lastAppCpuJiffies = 0L
    @Volatile private var lastAppCpuWallMs = 0L

    // Whether /proc/stat was readable on the first probe (cached to skip retries).
    @Volatile private var sysCpuAvailable = true

    // Cached GPU devfreq load path (null = not yet resolved, empty = none found).
    @Volatile private var resolvedGpuDevfreqPath: String? = null

    // Cached CDSP load path (null = not yet resolved, empty = none found).
    @Volatile private var resolvedCdspPath: String? = null

    /**
     * Opens the CSV file and begins writing one row per second.
     *
     * If the logger is already running this call is a no-op.
     */
    fun start() {
        if (isRunning.getAndSet(true)) return

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val outDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outDir, "EdgeVisionAI-${appTag}_log_$timestamp.csv")

        writer = PrintWriter(FileWriter(file, false))
        writer?.println(CSV_HEADER)
        writer?.flush()
        Log.i(TAG, "Performance logging started → ${file.absolutePath}")

        // Prime deltas from the IO thread so memory visibility is guaranteed.
        logJob = scope.launch {
            primeCpuState()
            while (isRunning.get()) {
                delay(LOG_INTERVAL_MS)
                writeRow()
            }
        }
    }

    /**
     * Flushes the current buffer, closes the CSV file, and stops the logging coroutine.
     *
     * Safe to call even when the logger is not running.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return
        scope.launch {
            logJob?.cancelAndJoin()
            logJob = null
            writer?.flush()
            writer?.close()
            writer = null
            frameBuffer.clear()
            Log.i(TAG, "Performance logging stopped.")
        }
    }

    /**
     * Records a single frame's metrics into the internal buffer.
     *
     * Called from the camera analysis thread on every processed frame. No-op when not running.
     *
     * @param fps Current frames-per-second estimate from [FPSTracker].
     * @param convertMs Time to convert [ImageProxy] to [Bitmap], in milliseconds.
     * @param transformMs Time to rotate/transform the bitmap, in milliseconds.
     * @param inferenceMs Time for the detection pipeline inference, in milliseconds.
     * @param totalMs End-to-end frame processing time, in milliseconds.
     */
    fun onFrame(
        fps: Double,
        convertMs: Long,
        transformMs: Long,
        inferenceMs: Long,
        totalMs: Long,
    ) {
        if (!isRunning.get()) return
        frameBuffer.add(FrameSample(fps, inferenceMs, totalMs, convertMs, transformMs))
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Drains the frame buffer, computes averages, reads system metrics, and writes one CSV row.
     */
    private fun writeRow() {
        val samples = drainBuffer()

        val cpuPct = computeCpuPercent()
        val gpuPct = readGpuPercent()
        val dspPct = readDspPercent()
        val memMb = readMemoryMb()
        val rowTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        val fps = if (samples.isEmpty()) 0.0 else samples.map { it.fps }.average()
        val inferenceMs = if (samples.isEmpty()) 0L else samples.map { it.inferenceMs }.average().toLong()
        val totalMs = if (samples.isEmpty()) 0L else samples.map { it.totalMs }.average().toLong()
        val convertMs = if (samples.isEmpty()) 0L else samples.map { it.convertMs }.average().toLong()
        val transformMs = if (samples.isEmpty()) 0L else samples.map { it.transformMs }.average().toLong()

        val row = "$rowTimestamp,%.2f,%.2f,%.2f,%.2f,%.2f,$inferenceMs,$totalMs,$convertMs,$transformMs"
            .format(cpuPct, gpuPct, dspPct, memMb, fps)

        writer?.println(row)
        writer?.flush()
    }

    /**
     * Atomically drains all samples from [frameBuffer] into a list.
     */
    private fun drainBuffer(): List<FrameSample> {
        val result = mutableListOf<FrameSample>()
        while (true) {
            result.add(frameBuffer.poll() ?: break)
        }
        return result
    }

    // ── CPU ──────────────────────────────────────────────────────────────────────

    /**
     * Seeds CPU delta counters from the IO thread (guarantees [Volatile] visibility).
     */
    private fun primeCpuState() {
        val stats = readSysCpuStats()
        if (stats != null) {
            lastSysCpuTotal = stats.first
            lastSysCpuIdle = stats.second
            sysCpuAvailable = true
        } else {
            sysCpuAvailable = false
            val (jiffies, wallMs) = readAppCpuStats()
            lastAppCpuJiffies = jiffies
            lastAppCpuWallMs = wallMs
        }
    }

    /**
     * Reads the aggregate "cpu" line from `/proc/stat` and returns (totalJiffies, idleJiffies),
     * or `null` if the file is inaccessible (SELinux enforcement on `user` builds).
     */
    private fun readSysCpuStats(): Pair<Long, Long>? {
        return try {
            var result: Pair<Long, Long>? = null
            File("/proc/stat").forEachLine { line ->
                if (result == null && line.startsWith("cpu ")) {
                    val t = line.trim().split("\\s+".toRegex())
                    val user = t.getOrNull(1)?.toLongOrNull() ?: 0L
                    val nice = t.getOrNull(2)?.toLongOrNull() ?: 0L
                    val system = t.getOrNull(3)?.toLongOrNull() ?: 0L
                    val idle = t.getOrNull(4)?.toLongOrNull() ?: 0L
                    val iowait = t.getOrNull(5)?.toLongOrNull() ?: 0L
                    val irq = t.getOrNull(6)?.toLongOrNull() ?: 0L
                    val softirq = t.getOrNull(7)?.toLongOrNull() ?: 0L
                    val total = user + nice + system + idle + iowait + irq + softirq
                    if (total > 0L) result = total to idle
                }
            }
            if (result == null) Log.w(TAG, "cpu line not found or all-zero in /proc/stat")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read /proc/stat (${e.javaClass.simpleName}: ${e.message}). Falling back to app CPU.")
            null
        }
    }

    /**
     * Returns (appCpuJiffies, wallClockMs) for the current process from `/proc/self/stat`.
     *
     * This file is always readable by the owning process regardless of SELinux policy.
     * `appCpuJiffies` = utime + stime (fields 14 + 15 of `/proc/self/stat`).
     */
    private fun readAppCpuStats(): Pair<Long, Long> {
        return try {
            val parts = File("/proc/self/stat").readText().trim().split("\\s+".toRegex())
            val utime = parts.getOrNull(13)?.toLongOrNull() ?: 0L
            val stime = parts.getOrNull(14)?.toLongOrNull() ?: 0L
            (utime + stime) to System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read /proc/self/stat: ${e.message}")
            0L to System.currentTimeMillis()
        }
    }

    /**
     * Returns CPU utilisation (%) since the previous call.
     *
     * Uses system-wide `/proc/stat` when available; falls back to per-process `/proc/self/stat`
     * scaled by the number of available CPU cores.
     */
    private fun computeCpuPercent(): Double {
        return if (sysCpuAvailable) {
            computeSysCpuPercent()
        } else {
            computeAppCpuPercent()
        }
    }

    private fun computeSysCpuPercent(): Double {
        val stats = readSysCpuStats()
        if (stats == null) {
            sysCpuAvailable = false
            return computeAppCpuPercent()
        }
        val (total, idle) = stats
        val deltaTotal = total - lastSysCpuTotal
        val deltaIdle = idle - lastSysCpuIdle
        lastSysCpuTotal = total
        lastSysCpuIdle = idle
        return if (deltaTotal <= 0L) 0.0 else (deltaTotal - deltaIdle) * 100.0 / deltaTotal
    }

    private fun computeAppCpuPercent(): Double {
        val (jiffies, wallMs) = readAppCpuStats()
        val deltaJiffies = jiffies - lastAppCpuJiffies
        val deltaWallMs = wallMs - lastAppCpuWallMs
        lastAppCpuJiffies = jiffies
        lastAppCpuWallMs = wallMs
        if (deltaWallMs <= 0L) return 0.0
        // Convert jiffies to seconds: jiffies / USER_HZ; scale by number of cores.
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuSecs = deltaJiffies.toDouble() / USER_HZ
        val wallSecs = deltaWallMs / 1_000.0
        return (cpuSecs / (wallSecs * numCores)) * 100.0
    }

    // ── GPU (Qualcomm kgsl via devfreq load) ────────────────────────────────────

    /**
     * Dynamically discovers the GPU devfreq `load` path for the Qualcomm kgsl-3d0 GPU.
     *
     * Scans `/sys/class/devfreq/` for any entry whose name contains `"kgsl-3d0"` and caches
     * the result. Returns `null` when no such entry is found.
     *
     * The `load` file is maintained by the devfreq governor and contains a direct integer in the
     * range 0–100, so no delta arithmetic is required.
     */
    private fun resolveGpuDevfreqPath(): String? {
        resolvedGpuDevfreqPath?.let { cached ->
            return if (cached.isEmpty()) null else cached
        }
        val entry = File("/sys/class/devfreq").listFiles()?.find { "kgsl-3d0" in it.name }
        return if (entry != null) {
            val path = "${entry.absolutePath}/load"
            Log.i(TAG, "GPU devfreq load path resolved: $path")
            resolvedGpuDevfreqPath = path
            path
        } else {
            Log.w(TAG, "No kgsl-3d0 devfreq entry found. GPU utilisation will be 0.0.")
            resolvedGpuDevfreqPath = ""
            null
        }
    }

    /**
     * Returns GPU utilisation (%) read directly from the devfreq `load` file (0–100).
     *
     * Returns `0.0` when the devfreq interface is unavailable on this device.
     */
    private fun readGpuPercent(): Double {
        val path = resolveGpuDevfreqPath() ?: return 0.0
        return try {
            File(path).readText().trim().toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    // ── DSP (Qualcomm CDSP via devfreq) ─────────────────────────────────────────

    /**
     * Returns the Qualcomm CDSP (Hexagon DSP) utilisation percentage.
     *
     * On first call, probes [CDSP_LOAD_PATHS] to find the first readable sysfs file and caches
     * the result. Returns `0.0` when none of the paths are accessible (e.g. on non-Qualcomm
     * devices or when SELinux blocks access).
     *
     * The `load` file is updated by the kernel devfreq governor and typically contains a plain
     * integer in the range 0–100.
     */
    private fun readDspPercent(): Double {
        val path = resolveCdspPath() ?: return 0.0
        return try {
            File(path).readText().trim().toDoubleOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * Returns the cached CDSP load sysfs path, or `null` if none were found.
     *
     * On the first invocation all candidate paths are probed and the result is cached.
     */
    private fun resolveCdspPath(): String? {
        resolvedCdspPath?.let { cached ->
            return if (cached.isEmpty()) null else cached
        }
        for (candidate in CDSP_LOAD_PATHS) {
            try {
                val text = File(candidate).readText().trim()
                if (text.isNotEmpty()) {
                    Log.i(TAG, "CDSP load path resolved: $candidate (current value: $text)")
                    resolvedCdspPath = candidate
                    return candidate
                }
            } catch (_: Exception) {
                // not accessible, try next
            }
        }
        Log.w(TAG, "No readable CDSP load path found. DSP utilisation will be 0.0.")
        resolvedCdspPath = ""
        return null
    }

    // ── Memory ───────────────────────────────────────────────────────────────────

    /**
     * Returns the app's current total PSS memory usage in megabytes.
     */
    private fun readMemoryMb(): Double {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1_024.0
    }
}
