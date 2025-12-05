# 性能监控 SDK 作业说明

## 项目概览

| 模块 | 作用 |
| --- | --- |
| `:performance-sdk` | 客户端性能监控 SDK，独立于 App，可被任意宿主集成。|
| `:app` | 演示 / 测试应用，展示流畅度与 ANR 数据，并提供卡顿、ANR 触发按钮。|

## 功能清单

- **流畅度监控**：通过 `Choreographer.FrameCallback` 统计帧间隔，计算 FPS、帧总数、Jank 次数。采样窗口可配置（默认 5 秒）。
- **ANR 监控**：后台看门狗线程周期性 ping 主线程，若在配置的 `timeoutMs` 内未响应则判断为 ANR，并抓取主线程堆栈。
- **可插拔回调**：`PerformanceConfig` 中的 `SmoothnessConfig.onMetrics`、`AnrConfig.onAnr` 均由宿主注入，便于上传或自定义展示。
- **演示 UI**：Compose 仪表板实时展示指标，并内置“触发 UI 卡顿”和“触发 ANR”按钮，方便课堂 Demo。

## 架构说明

```
PerformanceSdk
 └── PerformanceMonitor
      ├── SmoothnessTracker (FpsMonitor)
      └── AnrWatcher (watchdog)
```

- `PerformanceSdk.start()` 负责单例初始化，传入 `Application` 与 `PerformanceConfig`。
- `PerformanceMonitor` 同时驱动 `FpsMonitor` 与 `AnrWatcher`，并统一生命周期 (`start/stop`)。
- `FpsMonitor` 监听帧回调，持续累积帧数据，并在协程中定期计算平均 FPS/Jank 比例。
- `AnrWatcher` 使用 `HandlerThread` + `CountDownLatch` 检测主线程阻塞，并在触发时生成 `AnrEvent`（时间戳 + 主线程堆栈）。
- Demo App 通过轻量事件总线将 SDK 回调转给 Compose UI，实现 SDK 与页面解耦。

## SDK 接入步骤

1. **settings.gradle.kts** 中 `include(":performance-sdk")`（已配置）。
2. 在宿主模块 `build.gradle.kts` 中声明依赖：
   ```kotlin
   implementation(project(":performance-sdk"))
   ```
3. 在 `Application` 或首个 `Activity` 中初始化：
   ```kotlin
   class DemoApp : Application() {
       override fun onCreate() {
           super.onCreate()
           PerformanceSdk.start(
               application = this,
               config = PerformanceConfig(
                   smoothnessConfig = SmoothnessConfig(sampleWindowSeconds = 2) { metrics ->
                       // TODO: 上传或展示 metrics
                   },
                   anrConfig = AnrConfig(timeoutMs = 4000) { event ->
                       // TODO: 上报 ANR
                   }
               )
           )
       }
   }
   ```
4. 在适当位置（例如 `Application.onTerminate` 或 `Activity.onDestroy`）调用 `PerformanceSdk.stop()` 以释放监听。

## 演示应用使用方式

1. 启动 App，首页即为性能控制台：
   - "Smoothness metrics" 卡片显示最近一个窗口的帧数 / Jank / FPS。
   - "ANR status" 卡片显示最近一次 ANR 的触发时间与主线程堆栈。
2. 点击 **Trigger UI jank workload** 启动短时 busy-loop + `delay`，模拟掉帧，可观察 FPS、Jank 变化。
3. 点击 **Trigger ANR (freeze main thread)** 会休眠主线程 6 秒，触发 ANR Watcher，卡片将打印堆栈。

## 运行 / 构建

尚未在当前提交中执行构建，可按需运行：

```powershell
cd D:\app\Android_Studio_Project\project4
./gradlew :app:assembleDebug
```

编译成功后可通过 Android Studio 安装 `app` 模块进行体验。

## 后续扩展建议

1. **更精细的流畅度指标准确性**：结合 `FrameMetricsAggregator`（API 24+）或 AndroidX `JankStats` 获取更全面的帧指标。
2. **ANR SIGQUIT 支持**：监听 `/data/anr/traces.txt` 或 `Debug.dumpNativeBacktrace`，捕获系统 SIGQUIT 时的全量堆栈，满足自由发挥要求。
3. **数据持久化/上传**：为 SDK 增加接口，让宿主可选择本地存储、日志输出或网络上报。
4. **自动化测试**：编写 Instrumentation Test，通过 `UiAutomator` 或自定义 IdlingResource 验证 jank/anr 回调是否触发。

