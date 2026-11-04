# DLBluetoothUtil 1.1.0

本版修复跨平台连接取消、操作队列异常及通知状态问题，新增Apple Silicon iOS模拟器制品。公共BLE API签名保持兼容。

## 安装

通过 [D10NGYANG/maven-repo](https://github.com/D10NGYANG/maven-repo) 的main分支安装Maven制品：

```kotlin
maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository")
```

```kotlin
implementation("com.github.D10NGYANG:DLBluetoothUtil:1.1.0")
```

支持Android、iOS arm64、iOS simulator arm64、JS、WasmJS。Android独立平台坐标仍为`DLBluetoothUtil-android:1.1.0`。

## 主要修复

- Android/iOS单次原生异常不再终止全局操作队列；修复未交付连接取消泄漏及恢复信号竞态。
- Android最大写入payload限制为`minOf(MTU - 3, 512)`；超长写入立即返回参数异常。
- Android通知配置失败恢复先前本地注册；iOS特征发现错误正确传播。
- Web取消连接清理晚到结果；旧连接重复断开不影响新连接；断开后不再因迟到结果重新安装监听器。
- Demo修复连接生命周期、多服务同UUID特征状态、INDICATE开关和Web并发GATT操作。

## 构建与依赖

升级Gradle9.8.2（含安全修复）、AGP9.5.0、Kotlin2.4.21、Compose1.12.2、Datetime0.8.0、Versions0.67.0。Material3保持稳定版1.9.0，DLLogUtil保持0.2.1。

采用官方Android KMP Library插件；Android应用入口拆至`androidDemo`，包名不变。compileSdk37、targetSdk36、minSdk26；库AAR不强制下游使用compileSdk37。移除闲置直接依赖和JitPack，以官方Material Symbols资源替代旧图标库。

## 升级注意

- API签名不变，但应检查超长写入参数异常、iOS发现失败反馈、Web取消后再次连接的时序。
- 普通操作超时不主动断开已交付连接的契约不变。
- 本仓库源码安装命令改为`:androidDemo:installDebug`，Android库测试改为`:DLBluetoothUtil:testAndroidHostTest`。
- 已验证Kotlin2.4.21多平台消费者和Kotlin2.3.21/AGP8.13.2的Android消费者；不承诺所有旧Native/JS编译器兼容。

[完整升级指南](https://github.com/D10NGYANG/DLBluetoothUtil/blob/dev/docs/MIGRATION_1.1.0.md) · [更新日志](https://github.com/D10NGYANG/DLBluetoothUtil/blob/1.1.0/CHANGELOG.md)

## 验证

78次跨平台测试执行通过（Android17、JS24、Wasm20、iOS模拟器17），并通过Android Lint、Web生产构建、Xcode模拟器构建、发布制品与独立消费者检查。华为API31扫描和iOS/Web界面验证通过。

模拟器不替代真实BLE收发；未执行iPhone真机及全部外设故障测试。上游非阻塞警告和验证边界见[验收记录](https://github.com/D10NGYANG/DLBluetoothUtil/blob/1.1.0/docs/DEPENDENCY_UPGRADE_2026-11-03.md)。
