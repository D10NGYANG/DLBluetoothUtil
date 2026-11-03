# 依赖升级与迁移验收

日期：2026-11-03。承接依赖审计建议，已实施并验证；未提交Git、未发布到远程仓库。

## 最终版本

- Gradle 9.8.2（官方生成Wrapper，并配置distributionSha256Sum）。由于本轮包含AGP9迁移，采用已修复安全公告的9.x版本，而不是只停留8.14.6。
- AGP 9.5.0；Kotlin/KMP/Compose Compiler 2.4.21。
- Compose Multiplatform 1.12.2；Material3显式保留稳定版1.9.0，不采用alpha。
- kotlinx-datetime 0.8.0；Versions插件迁移为`io.github.ben-manes.versions:0.67.0`，使用内置预发布过滤。
- Coroutines1.11.0、Activity1.13.0、Startup1.2.0、Foojay1.0.0及DLLogUtil0.2.1保持。
- Android compileSdk37、targetSdk36、minSdk26；库AAR的minCompileSdk显式设为26，避免让纯BLE下游被Demo的UI构建要求锁定到37。

Gradle9.8.2/AGP9.5.0超出Kotlin2.4.21文档所列完整测试范围上限；本轮完成项目实际构建和回归，不能据此扩展为所有插件/项目组合均受官方保证。详细官方来源见DEPENDENCY_AUDIT_2026-11-03.md。

## 模块与依赖调整

- `lib`、`mobileDemo`使用官方`com.android.kotlin.multiplatform.library`，保留Android、iOS真机/模拟器及原Web目标。
- 新增`androidDemo`应用模块，承接原mobileDemo/androidMain的Activity、Manifest、资源。迁移文件逐字节核对一致；包名仍是com.d10ng.bluetooth.demo。共享UI与MobileDemo Framework仍留在mobileDemo。
- Android主机测试显式启用，任务由testDebugUnitTest改为testAndroidHostTest。应用安装任务改为`:androidDemo:installDebug`。
- 删除未直接使用的Lifecycle声明、重复activity-ktx、未应用Hot Reload与kotlin-android插件声明、JitPack仓库；保留自有Maven仓库。Lifecycle仍由Compose传递提供，并非从产物完全消失。
- 两个Demo的Material Icons库替换为10个按需Material Symbols矢量资源，保留可访问名称与返回箭头RTL镜像；来源及许可证见MATERIAL_SYMBOLS.md和MATERIAL_SYMBOLS_LICENSE.txt。
- Compose依赖改为显式版本目录坐标，更新Preview import；Android Compose Preview启用主机测试资源。
- 使用官方kotlinUpgradeYarnLock/kotlinWasmUpgradeYarnLock更新工具链生成依赖的锁文件，未手改生成package.json。
- CI Java改为21；README和架构文档更新构建入口。正常构建不再强制提供发布凭据，实际远程发布仍需正确凭据。

## 自动化结果

| 验证 | 结果 |
| --- | --- |
| Android testAndroidHostTest | 17项通过 |
| JS浏览器测试 | 24项通过 |
| Wasm浏览器测试 | 20项通过 |
| iOS Simulator arm64测试 | 17项通过 |
| Android Debug APK | 构建通过、华为手机覆盖安装成功 |
| Android lintDebug | 0 errors，6 warnings |
| JS/Wasm生产构建 | 两者通过 |
| iOS arm64库、模拟器共享UI | 编译通过 |
| Xcode Simulator App | BUILD SUCCEEDED、安装启动成功 |
| 五平台发布元数据及制品 | 临时仓库生成通过 |
| 新工具链独立消费者 | Android、JS、Wasm、iOS arm64/simulator编译通过，Android测试通过 |
| 旧工具链独立Android消费者 | Kotlin2.3.21、AGP8.13.2、Gradle8.14.6、compileSdk36，编译及测试通过 |
| Versions0.67.0 dependencyUpdates | 任务运行通过；仍存在KMP cinterop变体探测unresolved，不当作实际构建失败 |

78次执行含跨平台重复的common/web用例，不代表78个独立用例。独立消费项目只从`/tmp/ble-upgrade-maven`取得此次生成制品，未依赖当前工程project引用；Android AAR确认保留Startup provider和元数据，坐标仍为DLBluetoothUtil-android。

datetime更新工具将0.8.0-0.6.x-compat列为候选，它是兼容分支而不是本项目应采用的新功能稳定版，保留0.8.0。

## 实际运行

- 华为API31：启动升级APK，蓝牙关闭时扫描显示错误；通过系统开启蓝牙后发现4个可连接设备；停止扫描，结束Demo并恢复原蓝牙关闭状态。未连接或发送数据至附近未经指定的外设。
- iOS27模拟器：共享测试运行通过，Xcode构建的App显示设备列表，Material Symbols正常显示。仅关闭本任务的iPhone18 Pro模拟器，未关闭其他任务设备。
- Web JS生产产物：Playwright自带独立Chromium加载页面，图标资源请求200、页面无控制台error；服务输入可编辑、扫描入口存在。Compose使用Canvas覆盖语义DOM，Playwright isEnabled不能准确代表灰显按钮且DOM click被Canvas拦截；按已观察画面点击发送位置后消息列表仍为空，符合未连接不可发送的行为。
- 浏览器自动化测试同样使用Playwright自带Chromium；未启动用户日常Chrome。Playwright会话ble-upgrade及本地HTTP服务已关闭。
- 内置浏览器初次访问localhost超时，生成一个失败临时标签；其错误页为工具不允许访问的data URL，显式清理接口也被拦截。该未标记标签按内置浏览器规则在本轮结束自动关闭；如界面仍保留“无法访问此站点”页，可直接关闭该任务页。

## 复检与已知限制

复用指定OCR delegate对当前工作区解析规则和检查差异；45个可审查文件全部核对（含前一轮修复与此次升级），Android移动文件按原内容比对，10个新增矢量XML解析通过。清单及排除项保存在验收目录。未发现新增阻塞性问题。

非阻塞警告：

- 部分传递Lifecycle2.9.x的自定义Lint使用旧API，被AGP跳过；其余Lint执行完成，不能把0 errors解释为这些被跳过检查也通过。
- targetSdk36不是最新37（符合已确认的Play目标要求），启动图标有旧资源目录/monochrome提示。
- Kotlin/Native链接器有缓存对象路径警告，实际链接与模拟器测试成功；Xcode原有两个Gradle脚本阶段未声明输出。
- Web生产构建有体积与source-map警告，不影响产物生成与已执行页面检查。

未覆盖iPhone真机BLE、真实外设读写、Android15/16超长写入故障注入、旧版Kotlin消费Native/JS klib及全量CVE扫描。新旧工具链兼容测试范围如上，不能扩大到所有下游版本。

完整证据位于 `/tmp/DLBluetoothUtil-upgrade-2026-11-03/`。CodeGraph已集中同步，无AOCI资产需维护。
