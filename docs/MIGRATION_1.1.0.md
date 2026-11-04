# 升级到 DLBluetoothUtil 1.1.0

## 安装

本库通过 [D10NGYANG/maven-repo](https://github.com/D10NGYANG/maven-repo) 提供 Maven 制品，按以下方式配置仓库与依赖。

```kotlin
// settings.gradle.kts
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

```kotlin
// KMP commonMain
implementation("com.github.D10NGYANG:DLBluetoothUtil:1.1.0")
// 仅 Android 工程也可以直接使用平台坐标
implementation("com.github.D10NGYANG:DLBluetoothUtil-android:1.1.0")
```

KMP 根模块通过 Gradle Module Metadata 自动选择 Android、iOS arm64、iOS simulator arm64、JS 或 WasmJS。
不要同时添加根坐标与 Android 平台坐标。DLLogUtil 仍为 0.2.1；直接使用日志控制类型的项目继续显式依赖它。

## 从1.0.x升级的行为变化

公共 ABleManager、ABleConnection 和模型 API 签名保持不变。

- Android `requestMaxMtu()` 仍返回 payload 字节数，但现在最多512。实际协商MTU为517时返回512而非514；不要额外扣3。按返回值自行分包。
- Android `write()` 对超过512字节的数据立即抛出参数异常；不能依靠平台拒绝后继续等待超时。
- 原生异常会作为当前调用失败传播，不再破坏全局操作队列。捕获失败时不要假设错误总是超时；CancellationException应继续向上传播。
- iOS特征发现失败现在导致discoverServices失败，不再返回缺少特征的“成功”快照。调用方应提供失败反馈或显式重试。
- Android CCCD失败会尝试恢复本地通知注册；如果操作超时，远端是否已经改变仍不确定。不要把本地回滚等同远端事务回滚。
- Web connect取消后会清理未交付连接，原生Promise结算前同设备再次connect会明确拒绝，避免旧尝试误断新会话；稍后再重试。
- Web已断开实例不可复用；旧实例重复disconnect不会影响后来建立的连接。

普通操作失败/超时不主动断开已交付连接的约定保持不变。底层迟到回调与端到端送达仍应由业务协议的序号、应答和重试处理。

## iOS模拟器

新增iosSimulatorArm64制品。Apple Silicon Mac可运行公共逻辑与UI测试；真实蓝牙扫描/连接/收发仍需iPhone/iPad及BLE外设。

```sh
./gradlew :DLBluetoothUtil:iosSimulatorArm64Test
```

## 库消费者与源码维护者分开升级

发布物由Kotlin2.4.21构建。已验证新的Kotlin2.4.21多平台消费工程，也验证Kotlin2.3.21 + AGP8.13.2 + Gradle8.14.6 + compileSdk36的Android消费工程。
这些结果不代表任意旧版Kotlin都能消费新Native/JS klib。遇到元数据或klib版本错误时先升级到2.4.21；生产升级请执行自身平台回归。

消费者不需要因为使用本库就迁移其整个工程到AGP9或compileSdk37。库仍要求Android minSdk26；AAR minCompileSdk为26，传递依赖另有要求时以Gradle检查为准。

本仓库源码构建升级到Gradle9.8.2、AGP9.5.0、Kotlin2.4.21。Compose Demo使用1.12.2，Material3固定稳定版1.9.0；Android应用compileSdk37、targetSdk36。

- `mobileDemo`保留共享UI和MobileDemo iOS Framework。
- Android启动Activity、Manifest和资源移至`androidDemo`，应用包名不变。
- 原`:mobileDemo:installDebug`改为`:androidDemo:installDebug`。
- 原库`:DLBluetoothUtil:testDebugUnitTest`改为`:DLBluetoothUtil:testAndroidHostTest`。
- 库Android发布为单一variant，Maven Android坐标保持`DLBluetoothUtil-android`。
- 原旧图标库改为按需Material Symbols资源，不要求消费者跟随修改UI。

## 验证与限制

Android17、JS24、Wasm20、iOS模拟器17次测试执行通过；Android Lint无错误，Web两种生产构建、Xcode模拟器构建和独立消费者检查通过。华为API31手机验证了蓝牙开关与扫描，模拟器和Web验证了界面运行。

未覆盖iPhone真机BLE、所有外设协议、Android15/16超长写入硬件故障注入或所有旧编译器组合。上游部分Lifecycle Lint检查被跳过，以及Native缓存/Source Map/产物体积等警告见[升级验收记录](DEPENDENCY_UPGRADE_2026-11-03.md)。
