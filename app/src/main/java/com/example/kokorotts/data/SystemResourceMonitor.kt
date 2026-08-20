package com.example.kokorotts.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

class SystemResourceMonitor(private val context: Context) {

    private val _history = MutableStateFlow<List<ResourceDataPoint>>(emptyList())
    val history: StateFlow<List<ResourceDataPoint>> = _history.asStateFlow()

    private val _currentData = MutableStateFlow(
        ResourceDataPoint(
            cpuPercent = 0f,
            memoryPercent = 0f,
            memoryUsedMb = 0,
            memoryTotalMb = 0,
            temperatureCelsius = 30f
        )
    )
    val currentData: StateFlow<ResourceDataPoint> = _currentData.asStateFlow()

    private var monitorJob: Job? = null
    private val maxDataPoints = 60 // 60-second window

    private var lastProcCpuTime: Long = 0L
    private var lastRealTime: Long = 0L
    private val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    // Proc/stat fallback tracking
    private var lastTotalCpu: Long = 0L
    private var lastIdleCpu: Long = 0L

    fun startMonitoring(scope: CoroutineScope, intervalMs: Long = 1000L) {
        if (monitorJob != null) return

        lastProcCpuTime = Process.getElapsedCpuTime()
        lastRealTime = SystemClock.elapsedRealtime()

        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val point = sampleMetrics()
                _currentData.value = point

                val currentList = _history.value.toMutableList()
                currentList.add(point)
                if (currentList.size > maxDataPoints) {
                    currentList.removeAt(0)
                }
                _history.value = currentList

                delay(intervalMs)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun sampleMetrics(): ResourceDataPoint {
        val cpuUsage = calculateCpuUsage()
        val memInfo = sampleMemory()
        val temp = sampleTemperature()

        return ResourceDataPoint(
            timestamp = System.currentTimeMillis(),
            cpuPercent = cpuUsage,
            memoryPercent = memInfo.percent,
            memoryUsedMb = memInfo.usedMb,
            memoryTotalMb = memInfo.totalMb,
            temperatureCelsius = temp
        )
    }

    private fun calculateCpuUsage(): Float {
        val nowReal = SystemClock.elapsedRealtime()
        val nowProc = Process.getElapsedCpuTime()

        val deltaReal = nowReal - lastRealTime
        val deltaProc = nowProc - lastProcCpuTime

        lastRealTime = nowReal
        lastProcCpuTime = nowProc

        // First attempt: system-wide /proc/stat
        val sysCpu = readSystemCpuUsage()
        if (sysCpu >= 0f) {
            return sysCpu
        }

        // Second attempt: process CPU scaled
        if (deltaReal > 0) {
            val ratio = (deltaProc.toFloat() / (deltaReal * numCores).toFloat()) * 100f
            return ratio.coerceIn(0f, 100f)
        }

        return 0f
    }

    private fun readSystemCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val toks = load.split(" +".toRegex())
            if (toks.size >= 8 && toks[0] == "cpu") {
                val user = toks[1].toLong()
                val nice = toks[2].toLong()
                val system = toks[3].toLong()
                val idle = toks[4].toLong()
                val iowait = toks[5].toLong()
                val irq = toks[6].toLong()
                val softirq = toks[7].toLong()

                val total = user + nice + system + idle + iowait + irq + softirq
                val totalIdle = idle + iowait

                if (lastTotalCpu > 0L) {
                    val deltaTotal = total - lastTotalCpu
                    val deltaIdle = totalIdle - lastIdleCpu
                    lastTotalCpu = total
                    lastIdleCpu = totalIdle

                    if (deltaTotal > 0) {
                        val usage = ((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat()) * 100f
                        return usage.coerceIn(0f, 100f)
                    }
                } else {
                    lastTotalCpu = total
                    lastIdleCpu = totalIdle
                }
            }
            -1f
        } catch (_: Exception) {
            -1f
        }
    }

    private data class MemResult(val usedMb: Long, val totalMb: Long, val percent: Float)

    private fun sampleMemory(): MemResult {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            if (am != null) {
                am.getMemoryInfo(memInfo)
                val totalMb = memInfo.totalMem / (1024 * 1024)
                val availMb = memInfo.availMem / (1024 * 1024)
                val usedMb = (totalMb - availMb).coerceAtLeast(0)
                val percent = if (totalMb > 0) (usedMb.toFloat() / totalMb.toFloat() * 100f) else 0f
                MemResult(usedMb, totalMb, percent.coerceIn(0f, 100f))
            } else {
                val runtime = Runtime.getRuntime()
                val totalMb = runtime.totalMemory() / (1024 * 1024)
                val freeMb = runtime.freeMemory() / (1024 * 1024)
                val usedMb = totalMb - freeMb
                val percent = if (totalMb > 0) (usedMb.toFloat() / totalMb.toFloat() * 100f) else 0f
                MemResult(usedMb, totalMb, percent.coerceIn(0f, 100f))
            }
        } catch (_: Exception) {
            MemResult(0, 0, 0f)
        }
    }

    private fun sampleTemperature(): Float {
        // Battery temperature intent
        try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (tempTenths > 0) {
                return (tempTenths / 10.0f).coerceIn(10f, 90f)
            }
        } catch (_: Exception) {}

        // Fallback: check thermal zone files
        try {
            val thermalZoneDir = File("/sys/class/thermal/")
            if (thermalZoneDir.exists() && thermalZoneDir.isDirectory) {
                val zone0 = File(thermalZoneDir, "thermal_zone0/temp")
                if (zone0.exists() && zone0.canRead()) {
                    val raw = zone0.readText().trim().toFloatOrNull()
                    if (raw != null) {
                        return if (raw > 1000f) (raw / 1000f).coerceIn(10f, 90f) else raw.coerceIn(10f, 90f)
                    }
                }
            }
        } catch (_: Exception) {}

        // Default baseline simulation if running without sensors
        return 32.5f
    }
}
