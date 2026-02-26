# DLBluetoothUtil

![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-BLE-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-CoreBluetooth-black?logo=apple&logoColor=white)
![Web](https://img.shields.io/badge/Web-Bluetooth-4285F4?logo=google-chrome&logoColor=white)
![Coroutines](https://img.shields.io/badge/Kotlin-Coroutines-7F52FF?logo=kotlin&logoColor=white)
[![Latest](https://img.shields.io/badge/version-0.6.4-blue)](#)
[![GitHub stars](https://img.shields.io/github/stars/D10NGYANG/DLBluetoothUtil?logo=github)](https://github.com/D10NGYANG/DLBluetoothUtil/stargazers)

一个基于 Kotlin Multiplatform 的跨平台 BLE（Bluetooth Low Energy）通讯库。在 Android、iOS 以及 Web 环境下提供统一 API，用于设备扫描、连接、服务发现、写入与通知订阅等核心操作。仓库同时包含移动端与浏览器的示例代码，开箱即用。

- 库模块：`/lib`（KMP + Android/iOS/JS/Wasm）
- 移动端示例：`/mobileDemo`（Android + iOS Framework）
- Web 示例：`/webDemo`（Kotlin/JS + Wasm）

**在线预览：[https://d10ngyang.github.io/DLBluetoothUtil/](https://d10ngyang.github.io/DLBluetoothUtil/)**

## 支持特性

- 统一管理器与连接抽象：`ABleManager` / `ABleConnection`
- 设备扫描、连接与断开（Android/iOS/Web 均支持）
- 服务发现（GATT Service/Characteristic 模型统一）
- 特征值写入（Write / Write Without Response）
- 通知/指示订阅与数据流转（`notifyDataFlow`）
- MTU 尝试与约束常量（`GATT_MAX_MTU_SIZE=517`）
- 基于 Kotlin Coroutines/Flow 的异步事件流
- Web 端对 Web Bluetooth API 的适配（Chrome/Edge 等）

### 特征属性支持矩阵

| 属性                        | Android | iOS  | Web | 说明                                                          |
|---------------------------|---------|------|-----|-------------------------------------------------------------|
| `READ`                    | 不支持     | 不支持  | 不支持 | 当前未提供读取 API                                                 |
| `WRITE`（需要响应）             | 支持      | 支持   | 支持  | 自动选择写入类型并返回成功事件                                             |
| `WRITE_NO_RESPONSE`（无需响应） | 支持      | 支持   | 支持  | 高速写入；iOS 通过队列空闲事件回传                                         |
| `NOTIFY`                  | 支持      | 支持   | 支持  | 需启用 CCCD；数据通过 `notifyDataFlow` 下发                           |
| `INDICATE`                | 不支持     | 部分支持 | 支持  | iOS 底层可用，但当前仅校验 `NOTIFY`；Web 使用 `startNotifications()` 统一处理 |
| `SIGNED_WRITE`            | 不支持     | 不支持  | 不支持 | 暂未封装该写入类型                                                   |
| `BROADCAST`               | 不涉及     | 不涉及  | 不涉及 | 广播属性与客户端交互较少                                                |
| `EXTENDED_PROPS`          | 不涉及     | 不涉及  | 不涉及 | 扩展属性需按业务自定义读取/处理                                            |

## 支持平台

| 平台            | 代码开启蓝牙 | 最低版本/要求                      | 备注                                 |
|---------------|--------|------------------------------|------------------------------------|
| Android       | 支持     | `minSdk 26` / `targetSdk 34` | 需要运行时权限与定位服务（API < 31）；支持 MTU 请求   |
| iOS (arm64)   | 不支持    | `iOS 15.3+`（示例 `iosApp` 部署）  | 需在 Info.plist 添加蓝牙用途描述与可选后台模式      |
| Web (JS/Wasm) | 不支持    | 现代支持 Web Bluetooth 的浏览器      | 需要 HTTPS 或 localhost，且必须用户手势触发设备请求 |

## 安装与集成

支持通过 Maven 仓库或本地发布进行集成。

在 `settings.gradle.kts` 或项目仓库管理位置添加：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository") {
            mavenContent { includeGroupAndSubgroups("com.github.D10NGYANG") }
        }
        google()
        mavenCentral()
    }
}
```

在 KMP 模块的依赖中添加：

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.D10NGYANG:DLBluetoothUtil:0.6.4")
        }
    }
}
```

## 日志输出控制

本库内部使用 [DLLogUtil](https://github.com/D10NGYANG/DLLogUtil) 进行日志记录。若需要在你的项目中控制日志输出等级，或收集日志，请在你的 KMP 模块添加日志库依赖（建议在 `commonMain`）：

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // 日志库（用于控制输出等级）
                implementation("com.github.D10NGYANG:DLLogUtil:0.2.0")
            }
        }
    }
}
```

设置日志输出等级（示例）：

```kotlin
import com.d10ng.bluetooth.BluetoothManagerLog
import com.d10ng.log.LogLevel

fun initLogging() {
    // 仅输出 WARN 及以上级别
    BluetoothManagerLog.miniLevel = LogLevel.WARN

    // 如需输出更详细的日志（包含所有等级）
    // BluetoothManagerLog.miniLevel = LogLevel.VERBOSE
  
    // 关闭日志输出
    // BluetoothManagerLog.miniLevel = LogLevel.NONE
}
```

- 常见日志等级：`VERBOSE`、`DEBUG`、`INFO`、`WARN`、`ERROR`、`NONE`（具体以 DLLogUtil 定义为准）
- 推荐在应用启动时设置，如：Android 的 `Application.onCreate`、JVM/桌面项目的 `main` 函数、Web 页面初始化等


## 快速上手（Kotlin 示例）

```kotlin
import com.d10ng.bluetooth.getPlatformBleManager
import com.d10ng.bluetooth.ABleConnection
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.registerWebBleUseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// 分包工具：按照最大负载长度切分 ByteArray
fun ByteArray.chunkedBytes(size: Int): List<ByteArray> {
    if (size <= 0) return listOf(this)
    val chunks = mutableListOf<ByteArray>()
    var idx = 0
    while (idx < this.size) {
        val end = (idx + size).coerceAtMost(this.size)
        chunks += this.copyOfRange(idx, end)
        idx = end
    }
    return chunks
}

suspend fun demo(scope: CoroutineScope) {
    val ble = getPlatformBleManager()

    // 环境能力与蓝牙状态
    if (!ble.isSupported()) error("当前环境不支持 BLE")
    // 某些平台支持代码开启蓝牙（Android），iOS/Web 不支持
    if (ble.isSupportEnable()) ble.enable()

    // Web 端：在设备请求前注册计划使用的服务，否则无法获取服务和特征进行通讯
    // 可多次调用以注册多个服务 UUID；仅在 Web 平台有效
    registerWebBleUseService("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")

    // 扫描设备（Web 端一次只返回一个设备，且需要用户手势触发）
    val devices = mutableListOf<BleDevice>()
    ble.scan().collect { dev ->
        val valid = !dev.name.isNullOrBlank() && dev.name != "Unknown"
        if (valid && devices.none { it.address == dev.address }) devices += dev
    }

    val device = devices.firstOrNull() ?: return
    val conn = ble.connect(device)

    // 监听被迫断开（任意平台断开都会更新 isConnectedFlow）
    scope.launch {
        conn.isConnectedFlow.collect { connected ->
            if (!connected) {
                println("连接被断开：${device.address}")
                // TODO: 在此做重连或清理资源
            }
        }
    }

    // 发现服务/特征值
    val services = conn.discoverServices()
    val service = services.firstOrNull { it.uuid.equals("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", true) }
    val ch = service?.characteristics?.firstOrNull { it.uuid.equals("yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy", true) }

    // 订阅通知（需特征支持 NOTIFY/INDICATE）
    if (ch != null) {
        conn.notify(ch, true)
        scope.launch {
            conn.notifyDataFlow.collect { nd ->
                println("收到通知：uuid=${nd.characteristic.uuid} len=${nd.data.size}")
            }
        }
    }

    // 依据设备支持的最大负载（MTU相关）进行写入分包
    if (ch != null) {
        // 返回值可作为写入分包的最大长度；Web 端固定为 20
        val payloadMax: Int = conn.requestMaxMtu()
        val payload = ByteArray(200) { it.toByte() }
        val packets = payload.chunkedBytes(payloadMax)
        for (pkt in packets) {
            conn.write(ch, pkt)
        }
    }

    // 主动断开连接
    conn.disconnect()
}
```

## 接口说明

以下为跨平台统一抽象与数据模型，具体平台在 `androidMain` / `iosMain` / `jsMain` / `wasmJsMain` 下实现。

- `ABleManager`
  - `fun isSupported(): Boolean` 判断环境是否支持 BLE
  - `val isEnabledFlow: MutableStateFlow<Boolean>` 蓝牙模块开启状态
  - `fun isSupportEnable(): Boolean` 是否支持代码开启蓝牙（主要 Android）
  - `suspend fun enable()` 开启蓝牙（若支持）
  - `fun scan(): Flow<BleDevice>` 扫描设备（Web 端一次请求返回一个）
  - `suspend fun connect(device: BleDevice): ABleConnection` 连接设备，返回连接对象

- `ABleConnection`
  - `val isConnectedFlow: MutableStateFlow<Boolean>` 连接状态
  - `val servicesFlow: MutableStateFlow<List<BleGattService>>` 服务列表（发现后更新）
  - `val notifyStatusFlow: MutableStateFlow<List<BleGattCharacteristic>>` 已启用通知的特征列表
  - `val notifyDataFlow: MutableSharedFlow<BleGattNotifyData>` 通知数据事件流
  - `suspend fun discoverServices(): List<BleGattService>` 发现服务与特征值
  - `suspend fun requestMaxMtu(): Int` 请求最大写入 MTU（平台受限）
  - `suspend fun write(characteristic: BleGattCharacteristic, value: ByteArray)` 写入特征值
  - `suspend fun notify(characteristic: BleGattCharacteristic, enable: Boolean)` 开关通知/指示
  - `fun disconnect()` 断开连接

- 数据模型
  - `data class BleDevice(val name: String?, val address: String, val rssi: Int, val obj: Any?)`
  - `data class BleGattService(val uuid: String, val characteristics: List<BleGattCharacteristic>, val obj: Any? = null)`
  - `data class BleGattCharacteristic(val uuid: String, val serviceUuid: String, val properties: Set<BleGattCharacteristicProperty>, val obj: Any? = null)`
  - `data class BleGattNotifyData(val characteristic: BleGattCharacteristic, val data: ByteArray)`
  - `enum class BleGattCharacteristicProperty` 包含 `READ`, `WRITE`, `WRITE_NO_RESPONSE`, `NOTIFY`, `INDICATE` 等属性位

- Web 端额外接口
  - `fun registerWebBleUseService(uuid: String)` 在 Web 平台上注册计划访问的 GATT 服务 UUID；必须在调用 `scan()` 或触发 `navigator.bluetooth.requestDevice(...)` 前执行；可重复调用注册多个服务。否则无法获取到服务特征进行通讯。

## 注意事项

### Android
- 权限：API < 31 需要 `BLUETOOTH`、`BLUETOOTH_ADMIN`、`ACCESS_COARSE_LOCATION`、`ACCESS_FINE_LOCATION`；API ≥ 31 使用 `BLUETOOTH_SCAN`（含 `neverForLocation`）、`BLUETOOTH_CONNECT`。
- 位置服务：在 Android 10/11（API 29/30）上，扫描需要打开位置服务。
- 运行时权限：需在代码层发起权限请求，示例已处理（`mobileDemo`）。
- 特征写入与 MTU：`requestMaxMtu()` 的支持取决于设备与系统版本；发送数据需自行分包以满足 MTU 限制。

### iOS
- `Info.plist`：添加 `NSBluetoothAlwaysUsageDescription` 描述；如需后台扫描/连接，添加 `UIBackgroundModes` -> `bluetooth-central`。
- 蓝牙状态：只有在 `CBCentralManager` 处于 `PoweredOn` 时才能进行扫描与连接。
- 通知订阅：需写入 CCCD 以启用 Notification/Indication（库已做统一封装）。

### Web（Web Bluetooth）
- 安全环境：需要 `https` 或 `http://localhost`。
- 设备请求：`navigator.bluetooth.requestDevice(...)` 必须由用户手势触发（点击按钮等）。
- 服务注册：在调用 `scan()` 或 `navigator.bluetooth.requestDevice(...)` 之前，必须使用 `registerWebBleUseService("服务UUID")` 注册你计划访问的 GATT 服务（可多次调用注册多个）。未注册的服务在连接后不可见，导致无法获取特征进行通讯（浏览器需要通过 `optionalServices` 允许访问）。
- 扫描行为：一次请求返回一个设备，完成后 Flow 将关闭；如需多个设备，需多次请求。
- 兼容性：Chrome/Edge 支持较好；Safari/iOS 支持有限或不可用。

## 示例运行

- Android（移动端示例 `mobileDemo`）：
  - 命令行安装：`./gradlew :mobileDemo:installDebug`
  - 或使用 Android Studio 打开工程并运行 `mobileDemo`。

- iOS（Swift 示例 `iosApp`）：
  - 使用 Xcode 打开 `iosApp/iosApp.xcodeproj`，配置签名后直接运行。
  - `mobileDemo` 也会生成 iOS Framework，可供集成到现有 App（`linkIosArm64` 等任务）。

- Web（浏览器示例 `webDemo`）：
  - JS 开发运行：`./gradlew :webDemo:jsBrowserDevelopmentRun`
  - Wasm 运行：`./gradlew :webDemo:wasmJsBrowserRun`
  - 启动后使用浏览器访问本地地址并按提示进行设备请求。

## 参考资料

- [FastBLE](https://github.com/Jasonchenlijian/FastBle)
- [ble-starter-android](https://github.com/PunchThrough/ble-starter-android)
- [Android BLE 使用指南](https://punchthrough.com/android-ble-guide/)