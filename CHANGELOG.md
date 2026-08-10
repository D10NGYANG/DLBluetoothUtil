# 更新日志

本文记录 DLBluetoothUtil 的重要变更与升级适配方式。从 `1.0.0` 起按语义化版本管理：公共 API
不兼容调整提升主版本，向后兼容能力提升次版本，问题修复提升补丁版本。

## [1.0.0] - 2026-08-10

首个稳定版本。公共 BLE API 相比 `0.9.0` 保持不变，主要调整运行时可靠性、Android 集成职责、
完整诊断日志和构建依赖。

### 重点变化

- 普通服务发现、MTU、通知配置或写入失败、超时及调用协程取消，只结束本次操作，不再由库主动
  断开已经交付的连接。仅调用方显式 `disconnect()`，或 Android/iOS/Web 提供明确断开证据时清理连接。
- Android AAR 不再声明蓝牙或定位权限，也不再依赖 `DLAppUtil` 申请权限。应用必须在自己的
  Manifest 中声明权限，并在调用 `enable()`、`scan()`、`scanByAddress()`、`connect()` 前完成授权。
- Android `enable()` 使用 Application Context 发起系统蓝牙开启界面并返回；是否真正开启以
  `isEnabledFlow` 为准。
- Android/iOS 原生操作按设备地址串行，不同设备可以并发；修复快速连接/断开回调丢失、取消清理、
  iOS 写入与通知时序、Android Notification/Indication CCCD 等问题。
- 服务发现成功日志输出完整服务、特征和属性；BLE 收发 DEBUG 日志输出完整设备信息以及大写、连续、
  无空格 HEX payload，不进行设备数据脱敏。
- JS/Wasm 修复原生数组转换、`Uint8Array` 写入参数、`DataView` 切片和 DOM listener 清理。
- 工具链升级到 Gradle 8.14.5、AGP 8.13.2、Kotlin 2.3.21、Coroutines 1.11.0；demo 升级到
  Compose Multiplatform 1.11.1、Compose Hot Reload 1.2.0、AndroidX Activity 1.13.0。

### 升级迁移

完整步骤、Manifest 示例、行为检查清单和注意事项见
[从 0.9.0 升级到 1.0.0](docs/MIGRATION_1.0.0.md)。

## [0.9.0] - 2026-08-07

### 破坏性调整

- `ABleManager.isEnabledFlow` 改为只读 `StateFlow<Boolean>`。
- `ABleConnection` 的 `isConnectedFlow`、`servicesFlow`、`notifyStatusFlow` 改为只读
  `StateFlow`，`notifyDataFlow` 改为只读 `SharedFlow`。调用方不能再修改库内部状态。
- `BleDevice`、`BleGattService`、`BleGattCharacteristic` 不再是可由外部构造的 `data class`；
  平台句柄 `obj` 被移除，实例统一由扫描、已知设备查询和服务发现创建。
- `AndroidBleConnection`、`IosBleConnection`、JS/WasmJS 的 `WebBleConnection` 改为内部实现；
  连接必须通过 `ABleManager.connect()` 创建。
- `OperationType`、`OperationResult` 与 iOS peripheral 事件聚合器改为内部协议，不再作为公共
  API 暴露。
- 数据模型相等性改为稳定身份：设备按 `address`，服务按忽略大小写的 UUID，特征按忽略大小写的
  “服务 UUID + 特征 UUID”组合比较。名称、RSSI、特征属性与原生句柄不参与身份比较。

### 新增

- 新增 `ExperimentalNativeBleApi` 及各平台原生对象访问扩展，在显式 Opt-in 后可取得 Android
  `BluetoothDevice`/`BluetoothGatt`、iOS `CBPeripheral` 或 Web Bluetooth 对象。
- 新增 `BleDevice.withAdvertisement()`，用于保留设备身份和内部句柄并更新名称/RSSI 快照。
- 新增 [实现设计文档](lib/ARCHITECTURE.md)，说明跨平台边界、操作调度、回调时序、资源释放与
  原生逃生口约束。
- 新增同地址串行、不同地址并发、取消清理、锁等待超时和多服务同 UUID 特征订阅状态测试。

### 修复与行为调整

- Android/iOS 的原生 GATT 操作现在按设备地址串行，不同设备仍可并发；锁等待、入队和回调等待
  统一受操作超时约束，取消后可靠清理请求与锁注册表。
- 修复 Android 快速连接回调可能早于订阅建立而丢失的问题，并将高频通知与控制回调分流。
- Android 同时支持 Notification 与 Indication，写 CCCD 时选择正确的启用值。
- iOS 在发起服务发现、通知配置和有响应写入前先建立事件订阅，通知配置会等待系统确认；无响应
  写入仅在系统发送队列繁忙时等待 ready 回调。
- iOS `requestMaxMtu()` 直接返回 CoreBluetooth 的最大 payload 长度，不再错误地额外减去 3。
- 未知或失败的 iOS 通知不会终止连接监听协程；Android 旧版通知回调会安全处理空 payload。
- 通知订阅状态使用完整特征身份，避免不同服务下相同特征 UUID 相互覆盖。
- JS/WasmJS 读取通知 `DataView` 时保留 `byteOffset`/`byteLength`，避免把底层缓冲区的无关字节
  当作通知内容；重复订阅、取消订阅和断开时会正确释放对应 DOM listener。
- 所有连接实现都会在断开时清空服务和通知状态；通知事件使用 64 条有界缓冲并在满载时丢弃最旧
  数据，避免阻塞原生回调线程。

### 从 0.8.0 升级到 0.9.0

#### 1. 更新依赖

```kotlin
commonMain.dependencies {
    implementation("com.github.D10NGYANG:DLBluetoothUtil:0.9.0")
}
```

#### 2. 不再写入库的 Flow

收集方式不变；删除对 `.value` 的赋值。应用自己的 UI 或业务状态应保存在调用方的
`MutableStateFlow` 中。

```kotlin
// 0.8.0：不再允许
connection.isConnectedFlow.value = false

// 0.9.0：只观察库状态，通过公共操作改变连接生命周期
connection.isConnectedFlow.collect { connected ->
    if (!connected) reconnectOrUpdateUi()
}
connection.disconnect()
```

#### 3. 不再直接构造或 copy 数据模型

设备必须来自 `scan()`/`scanByAddress()`，服务与特征必须来自 `discoverServices()`。更新扫描展示
信息时使用 `withAdvertisement()`；若只需要 UI 模型，建议映射为业务自己的 `data class`。

```kotlin
// 0.8.0
val refreshed = oldDevice.copy(name = newName, rssi = newRssi)

// 0.9.0
val refreshed = oldDevice.withAdvertisement(name = newName, rssi = newRssi)
```

原先依赖 data class 解构的代码需改为命名属性访问：

```kotlin
val address = device.address
val name = device.name
val rssi = device.rssi
```

#### 4. 迁移 `obj` 原生句柄访问

优先使用跨平台 API。确需原生能力时，在对应平台源码集显式 Opt-in，并改用平台扩展：

```kotlin
@OptIn(ExperimentalNativeBleApi::class)
fun useAndroidNative(device: BleDevice, connection: ABleConnection) {
    val nativeDevice = device.androidBluetoothDevice()
    val gatt = connection.androidBluetoothGattOrNull() ?: return
    // 不要 close GATT、替换 callback，或与库的挂起 GATT 操作并发执行。
}
```

- Android：`androidBluetoothDevice()`、`androidBluetoothGattService()`、
  `androidBluetoothGattCharacteristic()`、`androidBluetoothGattOrNull()`。
- iOS：`iosPeripheral()`、`iosService()`、`iosCharacteristic()`、`iosPeripheralOrNull()`。
- JS/WasmJS：`webBluetoothDevice()`、`webBluetoothService()`、
  `webBluetoothCharacteristic()`、`webBluetoothGattOrNull()`。

这些扩展只存在于对应平台源码集，不能从 `commonMain` 调用。直接操作返回对象会绕过库的串行调度、
状态同步和资源管理，调用方必须自行保证互斥与生命周期。

#### 5. 适配相等性变化

不要再依赖名称、RSSI、特征属性或原生句柄区分同一设备/服务/特征。需要比较完整快照时，请在业务层
显式比较相关属性；需要用特征作键时，推荐使用 `serviceUuid.lowercase() to uuid.lowercase()`。

#### 6. 验证连接行为

- `requestMaxMtu()` 的返回值统一表示单次写入 payload 上限；不要再对 iOS 返回值额外减 3。
- 如果协议不能容忍通知丢包，请在业务 payload 中加入序号、确认与重传机制。
- 原有直接实例化平台 Connection 或调用内部 `OperationType`/`OperationResult` 的代码应改为
  `getPlatformBleManager()`、`connect()` 与公共 Connection 操作。

## 更早版本

`0.8.0` 及更早版本尚未回填逐版日志，可通过仓库 Git 历史和已发布 Maven 元数据查询。
