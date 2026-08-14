# DLBluetoothUtil 1.0.1 发布说明

`1.0.1` 是面向日志采集场景的补丁版本。公共 BLE API、扫描结果分发、连接状态机、写入与通知行为均
未改变；主要调整是减少正常路径中重复且可由相邻事件推导的日志。

## 升级

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.D10NGYANG:DLBluetoothUtil:1.0.1")
        }
    }
}
```

从 `1.0.0` 升级无需修改业务代码。如果日志平台按事件名或字段解析 BLE 日志，请检查下面的格式变化。

## 扫描日志

每次扫描广播仍输出一条 `[scan.result]`。Android 普通结果由：

```text
[scan.result] type=service_filter elapsedMs=46 callbackType=1 address=... name=... rssi=-66
```

精简为：

```text
[scan.result] address=... name=... rssi=-66
```

- `type` 已由同一次扫描的 `[scan.start]` 和 `[scan.stop]` 表达；
- `callbackType=1` 是 Android `CALLBACK_TYPE_ALL_MATCHES`，在当前扫描设置下没有额外诊断价值；
- `elapsedMs` 可以通过扫描开始和当前日志的时间戳计算；
- Android 批量扫描结果保留 `source=batch`，用于区分系统批量回调。

扫描启动、停止、权限拒绝、蓝牙关闭、扫描失败和结果投递失败日志均保留。

## 移动端操作日志

Android 和 iOS 不再为每次正常操作输出以下内部经过点：

```text
operation.queued
operation.recovery_ready
operation.started
operation.submitted
operation.runner_received
operation.completed
```

这些事件均可由公共入口、通讯内容和最终结果推导。以下异常路径仍保留完整 `operationId`、设备地址、
操作类型、特征信息和耗时：

```text
operation.timeout
operation.cancelled
operation.exception
operation.recovering
operation.recovered
```

## 写入和 GATT 回调

一次成功的有响应写入通常只记录：

```text
[ble.tx] ... op=... type=with-rsp
[write.success] operationId=... operation=write ...
```

底层 `gatt.characteristic_write` 成功回调不再重复输出。写入失败仍记录 `[write.failed]`，原生回调参数为空、
Android GATT status 或 iOS NSError 等诊断信息不会被移除。

连接、服务发现、通知配置和 MTU 同样移除了底层成功回调或内部中间结果，保留公共请求、最终就绪、失败、
超时、断开和清理异常。

## 通讯数据与安全

`[ble.tx]`、`[ble.rx]` 的完整 HEX payload、设备名称、地址、服务 UUID 和特征 UUID 格式保持不变。
日志仍可能包含设备标识和业务数据；全量落盘或上传时应继续限制访问权限、保存周期和外发范围。

## 验证

- Android release 单元测试；
- Android release 编译；
- iOS arm64 编译；
- Kotlin/JS 与 WasmJS 编译；
- Maven publication 元数据和各平台制品生成检查。
