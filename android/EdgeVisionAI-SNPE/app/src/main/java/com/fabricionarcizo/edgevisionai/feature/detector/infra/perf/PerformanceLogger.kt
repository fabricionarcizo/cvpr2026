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
 * When [start] is called, the logger opens a new CSV file and begins collecting per-frame
 * samples supplied via [onFrame]. Every [LOG_INTERVAL_MS] milliseconds it drains the sample
 * buffer, averages the frame-level metrics, reads system-level metrics (CPU, GPU, memory), and
 * appends a single row to the file.
 *
 * Call [stop] to flush and close the file. The logger is safe to start and stop multiple times.
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
         * CSV header written once at the top of each log file.
         */
        private const val CSV_HEADER =
            "timestamp,cpu_pct,gpu_pct,dsp_pct,memory_mb,fps,inference_ms,total_ms,convert_ms,transform_ms"
    }

    private val isRunning = AtomicBoolean(false)
    private val frameBuffer = ConcurrentLinkedQueue<FrameSample>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logJob: Job? = null
    private var writer: PrintWriter? = null

    // CPU delta state.
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L

    // GPU delta state (Qualcomm kgsl).
    private var lastGpuBusy = 0L
    private var lastGpuTotal = 0L

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

        // Prime deltas so the first row is not artificially high.
        primeCpuState()
        primeGpuState()

        logJob = scope.launch {
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
     * This method is called from the camera analysis thread on every processed frame. It is
     * a no-op when the logger is not running.
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
        val gpuPct = computeGpuPercent()
        val memMb = readMemoryMb()
        val rowTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        val fps = if (samples.isEmpty()) 0.0 else samples.map { it.fps }.average()
        val inferenceMs = if (samples.isEmpty()) 0L else samples.map { it.inferenceMs }.average().toLong()
        val totalMs = if (samples.isEmpty()) 0L else samples.map { it.totalMs }.average().toLong()
        val convertMs = if (samples.isEmpty()) 0L else samples.map { it.convertMs }.average().toLong()
        val transformMs = if (samples.isEmpty()) 0L else samples.map { it.transformMs }.average().toLong()

        val row = "$rowTimestamp,%.2f,%.2f,0.00,%.2f,%.2f,$inferenceMs,$totalMs,$convertMs,$transformMs"
            .format(cpuPct, gpuPct, memMb, fps)

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
     * Reads the first line of `/proc/stat` and returns (totalJiffies, idleJiffies).
     */
    private fun readRawCpuStats(): Pair<Long, Long> =
        try {
            val line = File("/proc/stat").bufferedReader().readLine() ?: return 0L to 0L
            val tokens = line.trim().split("\\s+".toRegex())
            // Fields: cpu user nice system idle iowait irq softirq steal...
            val user = tokens.getOrNull(1)?.toLongOrNull() ?: 0L
            val nice = tokens.getOrNull(2)?.toLongOrNull() ?: 0L
            val system = tokens.getOrNull(3)?.toLongOrNull() ?: 0L
            val idle = tokens.getOrNull(4)?.toLongOrNull() ?: 0L
            val iowait = tokens.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = tokens.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = tokens.getOrNull(7)?.toLongOrNull() ?: 0L
            val total = user + nice + system + idle + iowait + irq + softirq
            total to idle
        } catch (_: Exception) {
            0L to 0L
        }

    /**
     * Seeds the CPU delta counters so the very first [computeCpuPercent] call is meaningful.
     */
    private fun primeCpuState() {
        val (total, idle) = readRawCpuStats()
        lastCpuTotal = total
        lastCpuIdle = idle
    }

    /**
     * Returns the CPU utilisation (%) since the previous call, updating internal state.
     */
    private fun computeCpuPercent(): Double {
        val (total, idle) = readRawCpuStats()
        val deltaTotal = total - lastCpuTotal
        val deltaIdle = idle - lastCpuIdle
        lastCpuTotal = total
        lastCpuIdle = idle
        return if (deltaTotal <= 0L) 0.0 else (deltaTotal - deltaIdle) * 100.0 / deltaTotal
    }

    // ── GPU (Qualcomm kgsl) ──────────────────────────────────────────────────────

    /**
     * Reads `/sys/class/kgsl/kgsl-3d0/gpubusy` and returns (busyCount, totalCount).
     *
     * Returns (0, 0) on devices where the file is absent or unreadable.
     */
    private fun readRawGpuBusy(): Pair<Long, Long> =
        try {
            val text = File("/sys/class/kgsl/kgsl-3d0/gpubusy").readText().trim()
            val parts = text.split("\\s+".toRegex())
            (parts.getOrNull(0)?.toLongOrNull() ?: 0L) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        } catch (_: Exception) {
            0L to 0L
        }

    /**
     * Seeds the GPU delta counters so the very first [computeGpuPercent] call is meaningful.
     */
    private fun primeGpuState() {
        val (busy, total) = readRawGpuBusy()
        lastGpuBusy = busy
        lastGpuTotal = total
    }

    /**
     * Returns GPU utilisation (%) since the previous call, updating internal state.
     */
    private fun computeGpuPercent(): Double {
        val (busy, total) = readRawGpuBusy()
        val deltaBusy = busy - lastGpuBusy
        val deltaTotal = total - lastGpuTotal
        lastGpuBusy = busy
        lastGpuTotal = total
        return if (deltaTotal <= 0L) 0.0 else deltaBusy.coerceAtLeast(0L) * 100.0 / deltaTotal
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
