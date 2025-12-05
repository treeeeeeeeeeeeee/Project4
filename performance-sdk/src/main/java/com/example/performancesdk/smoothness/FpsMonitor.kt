package com.example.performancesdk.smoothness

import android.view.Choreographer
import com.example.performancesdk.SmoothnessConfig
import com.example.performancesdk.SmoothnessMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks frame pacing by subscribing to Choreographer callbacks and
 * periodically emitting aggregated metrics.
 */
class FpsMonitor(private val config: SmoothnessConfig) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var scope: CoroutineScope? = null
    private val mutex = Mutex()
    private var frameCounter = 0
    private var jankCounter = 0
    private var startTimeNs = 0L
    private var lastFrameTimeNs = 0L

    override fun doFrame(frameTimeNanos: Long) {
        scope?.let {
            it.launch {
                mutex.withLock {
                    if (startTimeNs == 0L) startTimeNs = frameTimeNanos
                    val durationNs = frameTimeNanos - lastFrameTimeNs
                    if (lastFrameTimeNs != 0L && durationNs > FRAME_BUDGET_NS) {
                        jankCounter++
                    }
                    frameCounter++
                    lastFrameTimeNs = frameTimeNanos
                }
            }
            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + Job())
        startTimeNs = 0L
        frameCounter = 0
        jankCounter = 0
        choreographer.postFrameCallback(this)
        scope?.launch { emitLoop() }
    }

    fun stop() {
        choreographer.removeFrameCallback(this)
        scope?.cancel()
        scope = null
    }

    private suspend fun emitLoop() {
        val windowMs = config.sampleWindowSeconds * 1000L
        while (scope != null) {
            delay(windowMs)
            val metrics = mutex.withLock {
                val elapsedNs = (System.nanoTime() - startTimeNs).coerceAtLeast(1L)
                val fps = frameCounter * 1_000_000_000.0 / elapsedNs
                SmoothnessMetrics(frameCounter, jankCounter, fps)
            }
            config.onMetrics(metrics)
            mutex.withLock {
                startTimeNs = System.nanoTime()
                frameCounter = 0
                jankCounter = 0
                lastFrameTimeNs = 0L
            }
        }
    }

    private companion object {
        private const val FRAME_BUDGET_NS = 16_666_667L // ~60 FPS budget
    }
}
