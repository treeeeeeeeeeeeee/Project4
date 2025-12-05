package com.example.performancesdk

import android.app.Application
import android.content.Context
import com.example.performancesdk.anr.AnrWatcher
import com.example.performancesdk.smoothness.FpsMonitor

/**
 * Entry point for the performance monitoring SDK. Must be initialized once from the host app.
 */
object PerformanceSdk {
    @Volatile
    private var monitor: PerformanceMonitor? = null

    @JvmStatic
    fun start(application: Application, config: PerformanceConfig = PerformanceConfig()): PerformanceMonitor {
        return monitor ?: synchronized(this) {
            monitor ?: PerformanceMonitor(application, config).also { instance ->
                monitor = instance
                instance.start()
            }
        }
    }

    @JvmStatic
    fun stop() {
        monitor?.stop()
        monitor = null
    }
}

/**
 * Internal orchestrator that wires smoothness and ANR trackers.
 */
class PerformanceMonitor(
    private val context: Context,
    private val config: PerformanceConfig
) {
    private val fpsMonitor = FpsMonitor(config.smoothnessConfig)
    private val anrWatcher = AnrWatcher(context, config.anrConfig)

    fun start() {
        fpsMonitor.start()
        anrWatcher.start()
    }

    fun stop() {
        fpsMonitor.stop()
        anrWatcher.stop()
    }
}

data class PerformanceConfig(
    val smoothnessConfig: SmoothnessConfig = SmoothnessConfig(),
    val anrConfig: AnrConfig = AnrConfig()
)

data class SmoothnessConfig(
    val sampleWindowSeconds: Int = 5,
    val onMetrics: (SmoothnessMetrics) -> Unit = {}
)

data class AnrConfig(
    val timeoutMs: Long = 5000,
    val onAnr: (AnrEvent) -> Unit = {}
)

data class SmoothnessMetrics(
    val frameCount: Int,
    val jankCount: Int,
    val averageFps: Double
)

data class AnrEvent(
    val timestampMs: Long,
    val mainThreadStack: String
)

