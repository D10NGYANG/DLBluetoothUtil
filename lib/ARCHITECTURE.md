# DLBluetoothUtil 实现设计

本文面向维护 `lib` 模块的开发者，说明当前实现的职责、调用链、并发与资源约束。公共用法请先看
[项目 README](../README.md)，本文描述的是实现决策，不承诺未写入公共 KDoc 的内部细节长期稳定。

## 1. 模块目标

`lib` 是一个 Kotlin Multiplatform BLE 客户端模块，目标是在 Android、iOS、Kotlin/JS 和
Kotlin/WasmJS 上提供同一组核心 Interface：

- 判断 BLE 能力与可用状态；
- 发现设备并建立 GATT 连接；
- 发现服务和特征；
- 协商或估算单次写入 payload 长度；
- 执行有响应/无响应写入；
- 开关 Notification/Indication 并接收数据；
- 在必要时显式取得平台原生对象。

模块不负责自动重连、业务协议编解码、分包重组、设备持久化、配对策略、链路加密策略、后台保活
或 READ 操作。这些能力应由上层业务组合，或在形成稳定跨平台语义后再加入公共 Interface。

## 2. Module、Interface 与平台 Seam

公共 Interface 刻意保持在少量类型上：

| 类型 | 职责 |
| --- | --- |
| `ABleManager` | 环境状态、设备发现、建立连接 |
| `ABleConnection` | 单个设备的 GATT 操作、连接状态与通知事件 |
| `BleDevice` | 平台无关的设备身份和广播快照 |
| `BleGattService` | 平台无关的服务与特征快照 |
| `BleGattCharacteristic` | 特征身份与能力集合 |
| `BleGattNotifyData` | 通知/指示 payload 事件 |

平台 Seam 位于 `ABleManager` 的 `expect/actual` 工厂以及 `ABleManager`、`ABleConnection` 的抽象
方法。四组 Adapter 位于各平台源码集：

| 源码集 | Manager Adapter | Connection Adapter | 原生实现 |
| --- | --- | --- | --- |
| `androidMain` | `AndroidBleManager` | `AndroidBleConnection` | Android BluetoothGatt |
| `iosMain` | `IosBleManager` | `IosBleConnection` | CoreBluetooth |
| `jsMain` | `WebBleManager` | `WebBleConnection` | Web Bluetooth dynamic interop |
| `wasmJsMain` | `WebBleManager` | `WebBleConnection` | Web Bluetooth external interfaces |

这个设计的 Depth 来自统一 Interface 隐藏了权限、扫描回调、delegate、CCCD、Promise、事件监听器
和资源释放差异。平台知识保持在 Adapter 附近，调用方不需要在业务代码中复制判断，维护者也能在
单个平台目录内完成大多数修复，形成 Locality。

## 3. 对象与状态模型

### 3.1 平台对象封装

`BleDevice`、`BleGattService`、`BleGattCharacteristic` 只能由库创建。它们包含 `internal`
`nativeHandle`，连接和 GATT 操作会把该句柄还原为平台对象。这样公共模型不会在 `commonMain`
泄漏 Android、CoreBluetooth 或 Web 类型。

相等性规则如下：

- `BleDevice` 只按平台地址比较；名称和 RSSI 是可变化的广播元数据；
- `BleGattService` 按忽略大小写的服务 UUID 比较；
- `BleGattCharacteristic` 按服务 UUID 与特征 UUID 的组合比较。

不要跨平台或跨进程持久化整个模型。持久化设备地址，重连时通过 `scanByAddress()` 取得带有新原生
句柄的实例。

### 3.2 只读状态流

Adapter 只修改 `mutable*Flow`，调用方只能看到对应的 `StateFlow`/`SharedFlow`：

- `isConnectedFlow`：连接是否有效；
- `servicesFlow`：最近一次服务发现快照；
- `notifyStatusFlow`：由库成功开启通知/指示的特征；
- `notifyDataFlow`：外设通知数据事件，不重放历史值。

断开时必须把连接状态设为 `false`，清空服务与订阅状态，并释放原生监听器或协程。状态 Flow 是
库对调用方的事实来源，不应通过原生逃生口单独修改连接状态。

`notifyDataFlow` 额外缓冲 64 条，缓冲满后使用 `DROP_OLDEST`。这保证原生 callback/delegate
不会被慢消费者阻塞，也避免内存无界增长；代价是高吞吐时可能丢通知。需要可靠传输的业务协议
必须在 payload 中设计序号、确认和重传。

## 4. 移动端操作调度

Android 与 iOS 的原生 BLE 操作通常是“发起调用，稍后收到回调”。共同调用链如下：

```text
Caller
  -> ABleConnection / ABleManager
  -> OperationManager.execute(operation)
  -> queueChannel
  -> AndroidOperationRunner / IosOperationRunner
  -> native BLE call
  -> callback / delegate event
  -> OperationRequest.result
  -> public result or exception
```

### 4.1 并发规则

`OperationManager` 为每个设备地址维护一个带引用计数的 `Mutex`：

- 同一地址从入队到结果/超时完整串行；
- 不同地址可以并发，Runner 会为每个请求启动独立子协程；
- 最后一个使用者离开后删除地址锁，避免注册表无限增长；
- 调用协程取消或操作超时会取消 `OperationRequest.result`，Runner 随之取消等待任务。

串行范围覆盖连接、服务发现、通知配置、写入和 MTU 请求。此规则保护 Android GATT 和
CoreBluetooth 的顺序约束，同时允许多个设备互不阻塞。

### 4.2 超时与错误

当前移动端超时值定义在 `OperationType`：

| 操作 | 超时 |
| --- | ---: |
| 连接 | 5000 ms |
| 服务发现 | 3000 ms |
| 通知配置 | 1000 ms |
| 写入 | 1000 ms |
| MTU | 1000 ms |

超时覆盖等待地址锁、入队、原生调用和回调等待。`OperationManager` 超时返回 `null`，Connection
Adapter 把失败转换为公共异常；MTU 失败是例外，它回退到默认 payload 长度 20。

Android/iOS 原生操作一旦发起，不一定能被协程真正撤销。超时后迟到的回调仍可能进入全局事件流；
当前关联条件主要是设备、GATT/peripheral、事件类型和特征，尚无请求 ID。连续对同一特征执行同类
操作时，迟到事件理论上可能被后续请求接收。扩展调度器时应优先评估为事件增加 generation/token，
或在超时后等待原生队列恢复，不能假设取消协程等于取消系统操作。

### 4.3 回调订阅顺序

Runner 使用 `async(start = CoroutineStart.UNDISPATCHED)` 先安装 Flow 订阅，再调用原生方法。
这个顺序是正确性的组成部分，不能改成“先调用、后等待”，否则快速回调会因 `SharedFlow` 无 replay
而丢失，最终表现为假超时。

Android 首次连接结果使用每次 `connectGatt()` 独立的 `CompletableDeferred`，而不是共享事件流，
确保 callback 早于 Connection collector 建立时仍能完成连接。

## 5. 控制事件与通知数据分流

Android 的 `BleGattCallbackInstant` 和 iOS 的 `BlePeripheralEvents` 都维护两条流：

- `eventFlow`：连接、服务发现、描述符写入、特征写入、MTU 等控制事件；
- `notificationFlow`：高频 characteristic value change 数据。

分流避免大量通知占满缓冲并挤掉控制确认，从而降低无关写入或服务发现超时的概率。Connection
Adapter 再按原生连接对象或设备地址过滤通知，并转换为公共 `BleGattNotifyData`。

控制事件流同样使用容量 64 的丢旧缓冲。正确性主要依赖“先订阅、后发起”和同地址串行，而不是依赖
缓冲保存历史事件。

## 6. 平台实现细节

### 6.1 Android

- 扫描使用 `SCAN_MODE_LOW_LATENCY`，提高发现速度但增加功耗；取消 Flow 会停止扫描；
- API 30 及以下会检查定位权限和定位服务，较新系统请求 Bluetooth 运行时权限；
- 通知通过 `setCharacteristicNotification` 和 CCCD descriptor write 两步完成；
- 同时支持 `NOTIFY` 与 `INDICATE`，优先使用 Notification；
- 写入优先选择有响应 WRITE，否则选择 WRITE_NO_RESPONSE；
- `requestMaxMtu()` 请求最大 517，并把原始 MTU 减 3 后作为 payload 长度返回；
- 主动断开立即更新公共状态，收到回调或等待 1 秒后关闭 GATT，关闭操作只执行一次。

### 6.2 iOS

- 地址是 `CBPeripheral.identifier.UUIDString`，不是 MAC；
- `scanByAddress()` 使用 `retrievePeripheralsWithIdentifiers`，只查询系统已知 peripheral；
- 所有 peripheral 共用 delegate，事件通过地址和特征身份关联；
- Notification 与 Indication 都由 `setNotifyValue` 统一开启，并等待状态回调确认；
- 无响应写入前检查 `canSendWriteWithoutResponse`，繁忙时等待 ready delegate 回调；
- iOS 不允许应用直接打开系统蓝牙；`enable()` 只能重建 central manager 以触发系统允许的提示流程；
- 最大写入长度直接使用 `maximumWriteValueLengthForType` 的结果；该 API 已返回 payload 上限，
  不再额外扣除 ATT 头。

### 6.3 Web JS/WasmJS

- `scan()` 实际调用 `navigator.bluetooth.requestDevice()`，不是持续被动扫描；
- `requestDevice()` 必须在安全上下文中由用户手势触发，通常选择一个设备后 Flow 结束；
- 浏览器只开放 filters 与 `optionalServices` 声明的服务，业务必须预先调用
  `registerWebBleUseService()`；
- `scanByAddress()` 使用 `navigator.bluetooth.getDevices()`，只能返回当前 Origin 已授权设备；
- GATT 操作直接等待浏览器 Promise，不经过 `OperationManager`，因此没有库级超时或同设备串行；
- 每个“服务 UUID + 特征 UUID”身份只保留一个 DOM listener，重复启用会替换旧 listener，
  断开时统一移除；
- 读取 `DataView` 时保留 `byteOffset` 与 `byteLength`，避免切片视图读取到缓冲区其他数据；
- 浏览器不开放 MTU 协商，固定返回 payload 长度 20。

调用方不应并发发起同一 Web 连接的多个 GATT 操作；浏览器可能直接拒绝并发 Promise。若未来需要
跨平台一致的调度语义，应为 Web 增加连接级 Mutex 和显式超时，而不是复用依赖原生回调的 Runner。

## 7. 原生逃生口

公共模型不直接暴露平台类型，但平台源码集提供标记为 `@ExperimentalNativeBleApi` 的扩展函数，
用于库尚未封装的原生能力。这样常见能力保持跨平台，少数平台功能又不必等待库发布新的公共抽象。

逃生口返回的是库正在使用的实时对象，不是副本。以下操作风险最高：

- Android 调用 `BluetoothGatt.close()` 或替换 callback；
- iOS 替换 `CBPeripheral.delegate`；
- Web 移除本库 listener 或直接断开 GATT；
- 在任意平台与库的挂起操作并发发起同一 GATT 操作。

这些调用绕过 `OperationManager` 和公共 Flow 状态同步。调用方必须显式 Opt-in，并自行保证互斥、
生命周期和错误处理。稳定且跨两个以上平台复用的能力，应提升到公共 Interface，而不是长期散落在
业务侧原生调用中。

## 8. 性能、安全与隐私

性能策略：

- 不同设备的移动端操作可并发，同一设备串行；
- Android 扫描偏向低延迟，业务应限制扫描时长以控制功耗；
- 通知路径使用 `tryEmit` 和有界缓冲，不阻塞原生线程；
- 服务和特征保存快照，避免调用方反复遍历原生对象；
- 断开时取消 Connection scope 并移除 Web listener，避免持续分发和资源泄漏。

安全与隐私约束：

- 日志只记录 payload 字节数，不记录完整 BLE 数据；
- 调试日志仍可能包含设备名称、地址和 UUID，生产环境应降低日志级别并按隐私要求处理；
- 本库不实现应用层加密、设备身份认证或配对策略，链路安全依赖操作系统与业务协议；
- Web 设备访问受 HTTPS/localhost、用户手势和 Origin 授权约束；
- 原生逃生口可能绕过状态机，只应在受控代码中使用。

## 9. 测试策略与覆盖缺口

common 测试当前验证：

- 同地址操作串行；
- 不同地址操作并发；
- 等待同地址锁的时间计入操作超时；
- 调用取消会取消待处理请求；
- 不同服务下相同 characteristic UUID 的通知状态互不覆盖。

继续扩展时应优先通过公共 Interface 验证可观察行为，避免测试依赖内部字段。平台 BLE 框架难以在
普通单元测试中替代，当前仍缺少以下自动化覆盖：

- 操作超时后的迟到回调隔离；
- Android/iOS 快速同步回调时序；
- Notification/Indication 开关与断开清理；
- 通知状态在多服务包含相同 characteristic UUID 时的更多平台集成场景；
- Web 重复订阅、断开监听器释放和并发 GATT 操作；
- 真机权限拒绝、蓝牙关闭、后台切换和外设异常断开。

至少应保留 common 调度测试、各目标编译检查，并在发布前执行 Android/iOS 真机冒烟测试。

## 10. 扩展指南

### 增加一个跨平台操作

1. 先定义调用方真正需要的公共语义、错误和状态变化，确认至少两个平台能给出一致含义。
2. 在 `ABleConnection` 或 `ABleManager` 增加最小 Interface，不把原生类型或 `Any` 暴露给调用方。
3. 为 Android/iOS 增加内部 `OperationType`、`OperationResult` 和 Runner 分支，并保持先订阅后调用。
4. 为 JS/WasmJS 分别实现 Promise Adapter，明确浏览器缺失能力时的回退或异常。
5. 更新状态 Flow、公共 KDoc、支持矩阵和本文件。
6. 增加 common 行为测试、目标编译验证和至少一个平台集成测试。

### 增加一个平台

1. 创建新的 KMP target 和源码集，实现 `getPlatformBleManager()` actual。
2. 实现 Manager 与 Connection Adapter，把平台对象只保存在 internal 句柄中。
3. 如果平台采用异步回调，建立平台私有 Runner；只有语义确实相同时才复用移动端调度器。
4. 明确地址身份、扫描生命周期、写入类型、MTU、通知确认和断开清理语义。
5. 提供受控的 `@ExperimentalNativeBleApi` 扩展，而不是扩大 common 模型构造器。
6. 更新文档、示例、测试与发布配置。

## 11. 维护不变量

修改 `lib` 时应持续满足：

- 公共 common Interface 不出现平台类型；
- 公共 Flow 只读，只有 Adapter 可修改状态；
- 同一移动端设备的未完成原生操作不主动并行；
- 等待原生回调的订阅先于原生调用建立；
- 控制事件和通知 payload 不共用缓冲；
- 断开操作幂等，并释放原生对象、协程或 DOM listener；
- 日志不输出完整 BLE payload；
- 原生逃生口必须显式 Opt-in，并在文档中说明绕过的保护。
