# DLBluetoothUtil

![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-BLE-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-CoreBluetooth-black?logo=apple&logoColor=white)
![Web](https://img.shields.io/badge/Web-Bluetooth-4285F4?logo=google-chrome&logoColor=white)
![Coroutines](https://img.shields.io/badge/Kotlin-Coroutines-7F52FF?logo=kotlin&logoColor=white)
[![Latest](https://img.shields.io/badge/version-1.0.1-blue)](#)
[![GitHub stars](https://img.shields.io/github/stars/D10NGYANG/DLBluetoothUtil?logo=github)](https://github.com/D10NGYANG/DLBluetoothUtil/stargazers)

一个基于 Kotlin Multiplatform 的跨平台 BLE（Bluetooth Low Energy）通讯库。在 Android、iOS 以及 Web 环境下提供统一 API，用于设备扫描、连接、服务发现、写入与通知订阅等核心操作。仓库同时包含移动端与浏览器的示例代码，开箱即用。

- 库模块：`/lib`（KMP + Android/iOS/JS/Wasm）
- 移动端示例：`/mobileDemo`（Android + iOS Framework）
- Web 示例：`/webDemo`（Kotlin/JS + Wasm）
- 实现设计：[lib/ARCHITECTURE.md](lib/ARCHITECTURE.md)
- 更新日志与升级迁移：[CHANGELOG.md](CHANGELOG.md)
- `1.0.1` 发布说明：[docs/RELEASE_1.0.1.md](docs/RELEASE_1.0.1.md)
- `1.0.0` 升级指南：[docs/MIGRATION_1.0.0.md](docs/MIGRATION_1.0.0.md)

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
| `INDICATE`                | 支持      | 支持   | 支持  | Android 写入 indication CCCD；iOS/Web 使用系统统一通知接口                 |
| `SIGNED_WRITE`            | 不支持     | 不支持  | 不支持 | 暂未封装该写入类型                                                   |
| `BROADCAST`               | 不涉及     | 不涉及  | 不涉及 | 广播属性与客户端交互较少                                                |
| `EXTENDED_PROPS`          | 不涉及     | 不涉及  | 不涉及 | 扩展属性需按业务自定义读取/处理                                            |

## 支持平台

| 平台            | 代码开启蓝牙 | 最低版本/要求                      | 备注                                 |
|---------------|--------|------------------------------|------------------------------------|
| Android       | 支持     | `minSdk 26` / `targetSdk 34` | 需要运行时权限与定位服务（API < 31）；支持 MTU 请求   |
| iOS (arm64)   | 不可直接开启 | `iOS 15.3+`（示例 `iosApp` 部署）  | `enable()` 仅能触发系统允许的提示流程；需配置用途描述 |
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
            implementation("com.github.D10NGYANG:DLBluetoothUtil:1.0.1")
        }
    }
}
```

从 `1.0.0` 升级到 `1.0.1` 不需要修改公共 BLE API，只需留意扫描和移动端操作日志的精简，参见
[1.0.1 发布说明](docs/RELEASE_1.0.1.md)。从 `0.9.0` 升级时，还需完成
[1.0.0 升级指南](docs/MIGRATION_1.0.0.md)中的权限、连接生命周期和日志数据检查；从 `0.8.0`
升级还需先完成[0.9.0 API 迁移](CHANGELOG.md#从-080-升级到-090)。

## 日志输出控制

本库内部使用 [DLLogUtil](https://github.com/D10NGYANG/DLLogUtil) 进行日志记录。若需要在你的项目中控制日志输出等级，或收集日志，请在你的 KMP 模块添加日志库依赖（建议在 `commonMain`）：

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // 日志库（用于控制输出等级）
                implementation("com.github.D10NGYANG:DLLogUtil:0.2.1")
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
- `DEBUG` 会输出完整的 BLE 收发数据，事件名为 `[ble.tx]` 和 `[ble.rx]`，payload 使用大写、连续的
  HEX 格式，并保留完整设备名称、地址、服务 UUID 和特征 UUID，便于按现场设备反查问题。
- 从 `1.0.1` 起，每条 `[scan.result]` 仅记录 `address`、`name` 和 `rssi`；扫描类型只在
  `[scan.start]`、`[scan.stop]` 中记录。移动端正常操作不再输出内部队列阶段和原生成功回调。
- 一次成功写入通常只产生 `[ble.tx]` 和 `[write.success]`；失败、超时、取消、恢复和原生错误日志
  仍保留完整的操作上下文。
- 通讯日志采用固定位置格式 `设备名@地址 服务UUID/特征UUID 字节数 HEX`，末尾仅按需附加 `op=`、
  `type=` 等短字段，例如：`[ble.tx] X1E@12:7B:56:E2:37:56 service/characteristic 7B 30302A34440D0A op=2 type=with-rsp`。
- 设备信息和通讯 payload 可能包含敏感数据；生产环境开启或收集 `DEBUG` 日志时，应限制日志访问、
  保存周期和外发范围。


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

    // 扫描设备：可按服务 UUID 过滤，为空时扫描所有设备
    // （Web 端一次只返回一个设备，且需要用户手势触发）
    val devices = mutableListOf<BleDevice>()
    ble.scan(serviceUuids = listOf("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")).collect { dev ->
        val valid = !dev.name.isNullOrBlank() && dev.name != "Unknown"
        if (valid && devices.none { it.address == dev.address }) devices += dev
    }

    // 或直接按已知地址查找设备（用于重连场景）
    // Android 传 MAC 地址；iOS 传 CBPeripheral identifier UUID；Web 传 device.id
    ble.scanByAddress(listOf("AA:BB:CC:DD:EE:FF")).collect { dev ->
        devices += dev
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
  - `val isEnabledFlow: StateFlow<Boolean>` 蓝牙模块开启状态（只读）
  - `fun isSupportEnable(): Boolean` 是否支持代码开启蓝牙（主要 Android）
  - `suspend fun enable()` 开启蓝牙（若支持）
  - `fun scan(serviceUuids: List<String> = emptyList()): Flow<BleDevice>` 按服务 UUID 过滤扫描，为空时扫描所有设备（Web 端一次请求返回一个）
  - `fun scanByAddress(addresses: List<String>): Flow<BleDevice>` 按设备地址查找已知设备（Android 传 MAC 地址，iOS 传 CBPeripheral identifier UUID，Web 传 device.id），适用于重连场景；iOS/Web 端从系统缓存直接返回，不启动扫描
  - `suspend fun connect(device: BleDevice): ABleConnection` 连接设备，返回连接对象

- `ABleConnection`
  - `val isConnectedFlow: StateFlow<Boolean>` 连接状态（只读）
  - `val servicesFlow: StateFlow<List<BleGattService>>` 服务列表（发现后更新，只读）
  - `val notifyStatusFlow: StateFlow<List<BleGattCharacteristic>>` 已启用通知的特征列表（只读）
  - `val notifyDataFlow: SharedFlow<BleGattNotifyData>` 通知数据事件流（只读）
  - `suspend fun discoverServices(): List<BleGattService>` 发现服务与特征值
  - `suspend fun requestMaxMtu(): Int` 获取单次写入的最大 payload 长度（Android 从 MTU 扣除 ATT 头，iOS 使用系统报告值，Web 固定为 20）
  - `suspend fun write(characteristic: BleGattCharacteristic, value: ByteArray)` 写入特征值
  - `suspend fun notify(characteristic: BleGattCharacteristic, enable: Boolean)` 开关通知/指示
  - `fun disconnect()` 断开连接

- 数据模型
  - `BleDevice` 提供设备名称、平台地址和信号强度；实例由库创建并持有内部平台句柄，可通过 `withAdvertisement()` 更新名称和信号强度
  - `BleGattService` 提供服务 UUID 和特征列表；实例由库创建
  - `BleGattCharacteristic` 提供特征 UUID、服务 UUID 和属性集合；实例由库创建
  - `data class BleGattNotifyData(val characteristic: BleGattCharacteristic, val data: ByteArray)`
  - `enum class BleGattCharacteristicProperty` 包含 `READ`, `WRITE`, `WRITE_NO_RESPONSE`, `NOTIFY`, `INDICATE` 等属性位

- Web 端额外接口
  - `fun registerWebBleUseService(uuid: String)` 在 Web 平台上注册计划访问的 GATT 服务 UUID；必须在调用 `scan()` 或触发 `navigator.bluetooth.requestDevice(...)` 前执行；可重复调用注册多个服务。否则无法获取到服务特征进行通讯。

- 平台原生访问（实验性）
  - 使用 `@OptIn(ExperimentalNativeBleApi::class)` 后，可通过平台源码集中的扩展函数获取 `BluetoothDevice`、`BluetoothGatt`、`CBPeripheral` 或 Web Bluetooth 对象
  - 原生访问用于库尚未封装的平台能力；直接断开连接、关闭 GATT 或并发发起 GATT 操作可能破坏库的状态与操作调度

## 注意事项

### Android
- 库 AAR 不声明蓝牙或定位权限，调用方必须在应用模块的 `AndroidManifest.xml` 中按需声明：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission
        android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />
    <uses-permission
        android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />
    <uses-permission
        android:name="android.permission.ACCESS_COARSE_LOCATION"
        android:maxSdkVersion="30" />
    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" />

    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
</manifest>
```

- 如果应用会通过蓝牙扫描推导物理位置，不应声明 `neverForLocation`，应根据业务用途调整定位权限配置。
- 位置服务：在 Android 10/11（API 29/30）上，扫描需要打开位置服务。
- 运行时权限：库只检查权限，不会主动弹出授权界面。调用方需在执行 `enable()`、`scan()`、
  `scanByAddress()` 或 `connect()` 前，通过 Activity Result API 或自身权限框架完成授权；缺少权限时
  操作会以异常结束。完整实现见 `mobileDemo` 的 `MainActivity`。
- `enable()` 会发起 Android 系统蓝牙开启界面并立即返回，不等待用户选择；最终状态以
  `isEnabledFlow` 为准。系统不允许当前进程启动界面时，该调用会抛出异常。
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
