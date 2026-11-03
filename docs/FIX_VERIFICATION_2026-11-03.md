# 全项目审查修复与验证

日期：2026-11-03。未提交或发布制品。

## 修复范围

已落实原报告12项修复：

1. Android/iOS Runner 在请求边界传播异常，普通原生异常不再取消唯一消费者。
2. Android payload 上限改为 `minOf(MTU - 3, 512)`，写入前拒绝超过512字节的数据，日志同步反映真实上限。
3. 移动端连接结果完成但调用方未领取时，取消路径释放原生连接；清理发生在释放地址互斥锁之前。
4. Web未交付连接取消后立即请求断开，并保留对未结算Promise的所有权；清理结束前拒绝同设备的新连接尝试。
5. 已断开的Web连接重复disconnect不再影响后续会话。
6. Android CCCD写入失败/取消时恢复之前的本地通知注册；回滚失败保留日志。超时后远端状态仍可能不确定，不能声称已回滚远端CCCD。
7. iOS特征发现失败抛出错误，不再把NSError转换成成功的空特征列表。
8. Web Demo的Flow收集归属LaunchedEffect，连接切换会取消旧任务。
9. Web Demo初始化、订阅及发送操作串行，失败可见，取消继续传播。
10. 两个Demo使用完整特征身份判断选择和订阅状态。
11. 两个Demo都提供INDICATE-only特征的订阅开关。
12. Demo在连接持有界面销毁时释放已交付连接。

修复期间复检还补充：每个请求独立保存恢复信号，防止“先清理后取消”永久阻塞后续请求；区分异常完成和CancellationException，避免普通异常触发iOS取消连接清理；Web迟到通知配置结果不能在断开后重新注册监听器。

## 测试

| 目标 | 执行用例数 | 结果 |
| --- | ---: | --- |
| Android JVM单元测试 | 17 | 通过 |
| Kotlin/JS浏览器测试 | 24 | 通过 |
| WasmJS浏览器测试 | 20 | 通过 |
| iosSimulatorArm64测试 | 17 | 通过 |

不同目标重复执行common/web用例，以上共78次执行，不代表78个独立测试。测试覆盖MTU边界、异常隔离、取消传播、连接交付竞态、提前恢复、晚到Web连接成功清理、旧连接重复断开及断开后的迟到通知结果。

编译检查：Android库及Demo APK、iOS真机arm64库、iOS Simulator arm64库、Web Demo JS/Wasm均通过。Xcode成功构建Simulator Demo，安装启动后显示设备列表。Kotlin/Native链接器有现有cache对象路径警告，任务最终成功。

浏览器测试通过既有Karma任务运行，CHROME_BIN指向Playwright自带的chrome-headless-shell，未启动用户日常Google Chrome。

## 实际行为检查

- 华为ADB手机（API 31）：更新Demo、冷启动成功；蓝牙关闭时扫描显示Bluetooth disabled；通过系统允许开启蓝牙后可扫描到附近设备；再次点击刷新停止扫描。
- 未连接或写入附近未经指定的外设。测试后停止Demo并恢复原先的蓝牙关闭状态。
- iOS 27 arm64模拟器：运行Kotlin/Native测试；Xcode构建、安装并启动Demo；设备列表界面正常显示。模拟器不能替代真实CoreBluetooth收发验证。
- 本任务启动的模拟器在交付前关闭，未关闭其他任务的设备或浏览器会话。

## OCR delegate复检

按用户指定技能执行workspace preview、逐文件规则解析、差异与关联调用审查。20个可审查代码/配置文件全部reviewed，skipped=0，coverage_rate=100%；Markdown由OCR过滤，另行核对。已处理上述复检问题，最终无遗留的已确认High/Critical问题。

## iOS模拟器使用

`lib`与`mobileDemo`已添加`iosSimulatorArm64()`，保留`iosArm64()`真机目标。

```sh
./gradlew :DLBluetoothUtil:iosSimulatorArm64Test
```

Apple Silicon Mac安装Xcode及iOS Simulator runtime后，可直接运行上述测试，或在Xcode选择iPhone Simulator运行iosApp。模拟器适合公共逻辑/协程/UI验证，蓝牙权限、射频扫描、真实GATT收发和平台错误回调仍需iPhone/iPad及BLE外设。

## 验证边界

没有进行Android 15/16真机513/514字节写入、真实外设CCCD失败回滚、iOS NSError回调与BLE收发的硬件故障注入；这些路径依据源码审查、边界/竞态测试及目标编译验证。没有扩大为全量依赖漏洞审计或修改既有“普通操作超时不主动断开已交付连接”的契约。

详细日志、XML测试结果、截图及OCR逐文件清单位于 `/tmp/DLBluetoothUtil-fix-verification-2026-11-03/`。
