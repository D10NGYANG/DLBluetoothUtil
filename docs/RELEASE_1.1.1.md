# DLBluetoothUtil 1.1.1

升级 DLLogUtil 至 0.3.0，改善跨平台日志可靠性；BLE 公共 API 与连接、扫描、写入逻辑保持不变。

## 安装

```kotlin
maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository")
```

```kotlin
implementation("com.github.D10NGYANG:DLBluetoothUtil:1.1.1")
// 直接使用日志控制类型时显式声明
implementation("com.github.D10NGYANG:DLLogUtil:0.3.0")
```

Android 独立平台坐标为 `DLBluetoothUtil-android:1.1.1`。KMP 根模块自动选择 Android、iOS arm64、iOS simulator arm64、JS 或 WasmJS 制品。

## 修复收益

- Apple 日志正确处理百分号、中文和 emoji，并修正时间戳起点。
- 日志等级跨线程可见，Logger 注册表并发访问更安全。
- 日志采集缓冲有界，避免高频 BLE 日志在慢消费者场景无限积压。

## 从1.1.0升级

仅更新依赖版本即可，无需改变 BLE API 调用。请核对以下日志行为：

1. `LoggerFactory.logDataFlow` 最多缓冲1024条，积压时丢弃最旧消息；不保证每条日志必达，也不重放无订阅者期间的历史日志。
2. 全局日志等级也应用到后续创建的Logger。本库的Bluetooth Logger按需创建；宿主预先设置DEBUG/VERBOSE后，可能开始打印完整HEX。需要单独控制时显式设置`BluetoothManagerLog.miniLevel`。
3. `getAll()`现在返回注册表快照，宿主如需最新标签列表应重新获取。
4. 新增atomicfu0.33.0运行时依赖，无需配置atomicfu编译器插件；协程保持1.11.0，Android minSdk保持26。

历史1.1.0制品不覆盖。更早版本迁移见[1.1.0升级指南](https://github.com/D10NGYANG/DLBluetoothUtil/blob/dev/docs/MIGRATION_1.1.0.md)。

[DLLogUtil 0.3.0发布说明](https://github.com/D10NGYANG/DLLogUtil/releases/tag/0.3.0) · [完整日志升级指南](https://github.com/D10NGYANG/DLLogUtil/blob/0.3.0/docs/upgrade-0.3.0.md)

## 验证范围

83次测试执行通过：Android18、JS25、Wasm21、iOS arm64模拟器19（包含跨平台重复用例）。完成Android、JS/Wasm浏览器与iOS arm64模拟器回归，补充BLE日志经过真实采集流后保持百分号、中文、emoji及HEX的集成测试，以及Apple平台实际日志输出测试；编译Android/Web Demo与iOS真机库。

模拟器验证不替代iPhone真机BLE收发；本版未重新进行外设协议或吞吐压力验收。
