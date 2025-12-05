package com.example.project4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.performancesdk.PerformanceConfig
import com.example.performancesdk.PerformanceSdk
import com.example.performancesdk.SmoothnessMetrics
import com.example.performancesdk.AnrEvent
import com.example.project4.ui.theme.Project4Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PerformanceSdk.start(
            application = application,
            config = PerformanceConfig(
                smoothnessConfig = com.example.performancesdk.SmoothnessConfig(
                    sampleWindowSeconds = 2,
                    onMetrics = { metrics -> PerformanceEventBus.emitSmoothness(metrics) }
                ),
                anrConfig = com.example.performancesdk.AnrConfig(
                    timeoutMs = 4000,
                    onAnr = { event -> PerformanceEventBus.emitAnr(event) }
                )
            )
        )
        setContent {
            Project4Theme {
                PerformanceDashboard(lifecycleScope)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PerformanceSdk.stop()
    }
}

object PerformanceEventBus {
    private val smoothnessListeners = mutableListOf<(SmoothnessMetrics) -> Unit>()
    private val anrListeners = mutableListOf<(AnrEvent) -> Unit>()

    fun emitSmoothness(metrics: SmoothnessMetrics) {
        smoothnessListeners.forEach { it(metrics) }
    }

    fun emitAnr(event: AnrEvent) {
        anrListeners.forEach { it(event) }
    }

    fun registerSmoothness(listener: (SmoothnessMetrics) -> Unit) {
        smoothnessListeners += listener
    }

    fun unregisterSmoothness(listener: (SmoothnessMetrics) -> Unit) {
        smoothnessListeners -= listener
    }

    fun registerAnr(listener: (AnrEvent) -> Unit) {
        anrListeners += listener
    }

    fun unregisterAnr(listener: (AnrEvent) -> Unit) {
        anrListeners -= listener
    }
}

@Composable
fun PerformanceDashboard(scope: CoroutineScope) {
    var latestMetrics by remember { mutableStateOf(SmoothnessMetrics(0, 0, 0.0)) }
    var latestAnr by remember { mutableStateOf<AnrEvent?>(null) }

    DisposableEffect(Unit) {
        val smoothListener: (SmoothnessMetrics) -> Unit = { metrics -> latestMetrics = metrics }
        val anrListener: (AnrEvent) -> Unit = { event -> latestAnr = event }
        PerformanceEventBus.registerSmoothness(smoothListener)
        PerformanceEventBus.registerAnr(anrListener)
        onDispose {
            PerformanceEventBus.unregisterSmoothness(smoothListener)
            PerformanceEventBus.unregisterAnr(anrListener)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Smoothness metrics", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Frames: ${'$'}{latestMetrics.frameCount}")
                    Text("Jank frames: ${'$'}{latestMetrics.jankCount}")
                    Text(String.format("FPS: %.1f", latestMetrics.averageFps))
                }
            }
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ANR status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    latestAnr?.let {
                        Text("Last ANR at: ${'$'}{it.timestampMs}")
                        Spacer(Modifier.height(4.dp))
                        Text(it.mainThreadStack)
                    } ?: Text("No ANR detected yet")
                }
            }
            StressButtons(scope)
        }
    }
}

@Composable
private fun StressButtons(scope: CoroutineScope) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { triggerUiJank(scope) }) {
            Text("Trigger UI jank workload")
        }
        Button(onClick = { triggerAnr(scope) }) {
            Text("Trigger ANR (freeze main thread)")
        }
    }
}

private fun triggerUiJank(scope: CoroutineScope) {
    scope.launch(Dispatchers.Main) {
        val end = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < end) {
            var busy = 0L
            while (busy < 3_000_000L) {
                busy++
            }
            delay(16)
        }
    }
}

private fun triggerAnr(scope: CoroutineScope) {
    scope.launch(Dispatchers.Main) {
        Thread.sleep(6_000)
    }
}