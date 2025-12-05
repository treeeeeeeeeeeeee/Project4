package com.example.performancesdk.anr

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.example.performancesdk.AnrConfig
import com.example.performancesdk.AnrEvent
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Simple ANR detector using a watchdog that ensures the main looper processes messages.
 */
class AnrWatcher(
    private val context: Context,
    private val config: AnrConfig
) {
    private val handlerThread = HandlerThread("PerformanceSdk-Anr")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = HeartbeatRunnable()
    private val handler: Handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    fun start() {
        handler.removeCallbacksAndMessages(null)
        handler.post(heartbeatRunnable)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }

    private inner class HeartbeatRunnable : Runnable {
        override fun run() {
            val ping = Object()
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                synchronized(ping) {
                    latch.countDown()
                }
            }
            val completed = latch.await(config.timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!completed) {
                config.onAnr(
                    AnrEvent(
                        timestampMs = System.currentTimeMillis(),
                        mainThreadStack = captureMainThreadStack()
                    )
                )
            }
            handler.postDelayed(this, config.timeoutMs)
        }
    }

    private fun captureMainThreadStack(): String {
        val stackTrace = Looper.getMainLooper().thread.stackTrace
        return buildString {
            stackTrace.forEach { element ->
                append(element.toString())
                append('\n')
            }
        }
    }
}
