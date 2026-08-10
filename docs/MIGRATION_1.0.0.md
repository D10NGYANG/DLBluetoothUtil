# DLBluetoothUtil 1.0.0 升级指南

本文适用于从 `0.9.0` 升级到 `1.0.0`。公共 BLE API 签名保持不变，已有扫描、连接、服务发现、
写入、通知和断开调用通常可以直接编译，但权限归属、连接失败策略、蓝牙开启结果和日志数据范围需要
调用方确认。

## 1. 更新依赖

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.D10NGYANG:DLBluetoothUtil:1.0.0")
        }
    }
}
```

如果项目通过版本目录管理依赖，只需把对应版本更新为 `1.0.0`。最低 Android SDK 仍为 26，公共
`ABleManager`、`ABleConnection` 和数据模型接口没有改名或删除。

## 2. Android 权限由应用声明

`1.0.0` 的库 AAR 不声明蓝牙或定位权限，也不会主动弹出运行时权限界面。请在应用模块的
`AndroidManifest.xml` 按业务需要声明：

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

只有应用能够保证扫描结果不会用于推导物理位置时才使用 `neverForLocation`；否则应按实际用途声明
定位权限。Android 11 及以下还要求定位服务处于开启状态。

在调用以下方法前，应用必须通过 Activity Result API 或自己的权限框架完成运行时授权：

- `enable()`：Android 12+ 需要 `BLUETOOTH_CONNECT`；
- `scan()`、`scanByAddress()`：Android 12+ 需要 `BLUETOOTH_SCAN`，Android 11 及以下需要定位权限；
- `connect()`：Android 12+ 需要 `BLUETOOTH_CONNECT`。

库仍会在访问系统 BLE API 前做防御性检查，权限缺失时操作失败，不会替应用发起授权。
`mobileDemo` 的 `MainActivity` 提供了 `RequestMultiplePermissions` 参考实现。

## 3. 监听蓝牙开启结果

Android `enable()` 现在只负责发起系统蓝牙开启界面，不等待用户选择结果。调用返回不等于蓝牙已经
开启，应以 `isEnabledFlow` 为准：

```kotlin
val manager = getPlatformBleManager()

scope.launch {
    manager.isEnabledFlow.collect { enabled ->
        if (enabled) startBleWorkflow()
    }
}

manager.enable()
```

不要在 `enable()` 返回后立即假设扫描一定可用。iOS 和 Web 的系统限制没有变化。

## 4. 调整连接失败与重试策略

已建立连接不会因为单次服务发现、MTU、通知配置或写入失败、超时、调用协程取消而被库主动断开。
调用方会收到当前操作失败，但 `isConnectedFlow` 可以继续保持 `true`。

推荐处理方式：

1. 当前操作按业务协议决定是否重试；
2. 写入是否真正被设备处理，以协议序号、设备应答和重传机制确认；
3. 不要仅因为一次写入超时就重建 BLE 连接；
4. 业务确认需要重连时，先显式调用 `connection.disconnect()`，再重新扫描或按地址获取设备并连接；
5. 设备或系统主动断开时，库依据原生回调更新 `isConnectedFlow=false` 并清理资源。

写入超时后，底层原生回调仍可能迟到。库不会自动重发 payload，也不把 GATT 回调当作业务协议应答，
避免重复执行设备命令。业务层必须继续使用已有发送应答协议判断端到端结果。

## 5. 检查日志采集策略

`DEBUG` 级别现在输出完整通讯内容：

```text
[ble.tx] 设备名@设备地址 服务UUID/特征UUID 7B 30302A34440D0A op=2 type=with-rsp
[ble.rx] 设备名@设备地址 服务UUID/特征UUID 7B 30302A34440D0A
```

HEX 使用大写连续格式，不插入空格。设备名称、地址、UUID 和 payload 不脱敏，服务发现成功日志还会
列出所有服务、特征和属性。这样便于现场问题反查，但也意味着日志可能包含设备标识、业务指令或敏感
数据。生产环境必须确认：

- 默认日志等级是否需要限制到 `INFO` 或 `WARN`；
- DEBUG 日志的访问权限、保存周期和上传范围；
- 崩溃/日志平台是否允许采集完整 BLE payload；
- 对外提供日志前是否需要由调用方执行合规处理。

如需调整日志等级，调用方继续使用 `BluetoothManagerLog.miniLevel`。由于公共日志控制类型来自
`DLLogUtil`，调用方需要直接使用该 API 时应显式声明 `DLLogUtil:0.2.1`，不要依赖传递依赖。

## 6. 依赖与构建注意事项

- `DLAppUtil` 已从库的 Android 运行时依赖中移除。如果应用自身使用其 API，请在应用模块显式声明；
- Coroutines 升级到 `1.11.0`，用于修复 SharedFlow 取消竞争和 R8 下 `shareIn/stateIn` 生命周期问题；
- 发布物使用 Kotlin `2.3.21`、AGP `8.13.2` 和 Gradle `8.14.5` 验证；
- 使用 Gradle Module Metadata 的 KMP 项目会自动选择 Android、iOS arm64、JS 或 WasmJS 变体；
- 本次没有迁移到 AGP 9，也没有改变 Android KMP publication 坐标。

库维护者不要直接把依赖检查插件升级到 `0.61.0`：它会把构建 classpath 的
`kotlin-daemon-client` 提升到 `2.4.10`，与当前 Kotlin `2.3.21` 编译器不兼容。当前使用 `0.54.0`。

## 7. 升级后验证清单

- Android 11 及以下：权限拒绝、定位关闭、扫描、连接和主动断开；
- Android 12 及以上：`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` 拒绝与授权后的完整流程；
- 蓝牙开启界面拒绝和接受后，`isEnabledFlow` 状态是否驱动正确业务流程；
- 单次写入或服务发现失败后，应用是否仍能按业务预期使用当前连接；
- 设备主动断开后，`isConnectedFlow` 是否变为 `false` 并触发调用方重连策略；
- Android/iOS 服务发现、Notification/Indication、有响应和无响应写入；
- JS/Wasm 浏览器设备授权、服务发现、写入和通知 listener 清理；
- 生产日志等级和完整 payload 的存储、上传及访问策略。

若项目仍使用 `0.8.x`，应先完成 [0.8.0 到 0.9.0 API 迁移](../CHANGELOG.md#从-080-升级到-090)，
再执行本文步骤。
