# DLLogUtil 0.3.0 跟进评估

日期：2026-11-04。当前DLBluetoothUtil使用DLLogUtil0.2.1，公共Maven元数据最新稳定版已为0.3.0。

结论：建议跟进，尤其是iOS可靠性。评估后已升级为DLBluetoothUtil 1.1.1并完成83次跨平台测试执行，详见[发布与升级说明](RELEASE_1.1.1.md)。

## 与本项目直接相关的改动

- Apple平台从将整条消息直接传给NSLog，改为固定格式加NSString参数。BLE日志含设备名和错误文本，可能出现百分号、中文及emoji，因此修复与本项目相关；同时修正Unix时间戳的错误基准。
- 日志等级改用StateFlow保证跨线程可见；本项目Demo在UI设置miniLevel，BLE回调/操作在其他线程输出，能直接受益。
- 注册表加跨平台锁；本库虽通过lazy创建Bluetooth Logger，整个应用仍可能在不同线程创建其他标签，工厂的共享注册表需要线程安全。
- 日志流改为1024条有界缓冲。高频扫描/HEX通讯日志在慢消费者场景不再无限积压，但旧日志可能被丢弃。

## 兼容与行为注意

本项目使用LoggerFactory.create、Logger.d/i/w/e和miniLevel，相关公开API不变。当前Kotlin2.4.21、Coroutines1.11.0、Android minSdk26与新库要求匹配。新库新增atomicfu0.33.0运行时依赖，不要求消费者安装编译器插件；Native/JS/Wasm仍需实际回归后才能确认集成通过。

0.3.0的全局等级也影响后续创建的Logger。本库Bluetooth Logger是惰性创建：宿主若先设全局DEBUG/VERBOSE，第一次BLE调用就可能开始输出完整HEX。需要单独控制时，通过BluetoothManagerLog显式覆盖等级。两个Demo已显式设置VERBOSE。

项目本身未收集LoggerFactory.logDataFlow，也未使用getAll，所以快照变化不直接影响当前实现；宿主的日志采集链路应接受缓冲满时丢旧日志，不能将其作为每条必达的审计通道。

## 最小跟进范围

1. 版本目录dllogutil改为0.3.0（同步作用于库与两个Demo），更新README中的日志依赖示例。
2. 保留历史1.1.0发布说明，新增补丁版本升级说明，说明缓冲和全局等级变化。
3. 跑Android、JS、Wasm、iOS Simulator回归及Demo编译，检查Apple百分号/中文日志、全局等级与传递依赖；确认后发布新的DLBluetoothUtil补丁版本，不能覆盖已发布1.1.0。

来源：[DLLogUtil 0.3.0 Release](https://github.com/D10NGYANG/DLLogUtil/releases/tag/0.3.0)、[升级指南](https://github.com/D10NGYANG/DLLogUtil/blob/0.3.0/docs/upgrade-0.3.0.md)、[Maven元数据](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLLogUtil/maven-metadata.xml)。已对照0.2.1到0.3.0源码差异及当前项目日志调用。
