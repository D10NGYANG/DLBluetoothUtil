# 依赖审计与官方替代建议

日期：2026-11-03。范围：当前工作区的lib、mobileDemo、webDemo、Gradle构建插件及可见的Web传递依赖。DL系列是项目自有库，不提出替换建议。本轮未修改版本、依赖声明或业务代码。

## 结论

- 应优先把Gradle Wrapper从8.14.5升级至8.14.6：它是修复三项High安全公告的同系列补丁，不必为此迁移Gradle 9。
- 库的非DL运行时直接依赖已经是Google/JetBrains官方组件，没有发现需要换成另一套BLE第三方框架的理由。
- Kotlin 2.4.21、datetime 0.8.0及Versions插件0.67.0值得评估；Compose 1.12.2必须与Android构建迁移一起处理，不能简单改版本。
- 优先精简Demo未直接使用的依赖、未应用的构建插件，并把旧Material Icons替换为官方推荐的Material Symbols矢量资源。

## 当前版本与建议

| 项目 | 当前 | 本次核实稳定版本/候选 | 建议 |
| --- | --- | --- | --- |
| Gradle Wrapper | 8.14.5 | 8.14.6，同系列；总体9.8.2 | 优先升级8.14.6 |
| AGP | 8.13.2 | 8.x仍为8.13.2；总体9.5.0 | 暂保留；9.x单独迁移 |
| Kotlin / KMP / Compose Compiler | 2.3.21 | 2.4.21 | 值得协调升级，三个插件保持同版本；验证已发布库消费者兼容性 |
| Compose Multiplatform | 1.11.1 | 1.12.2 | 有iOS/Web修复，但需先完成下述AGP/SDK迁移 |
| kotlinx-coroutines core/android/test | 1.11.0 | 1.11.0 | 保持 |
| AndroidX Startup | 1.2.0 | 1.2.0 | 保持 |
| AndroidX Activity | 1.13.0 | 1.13.0 | 保持；不采用1.14 alpha |
| kotlinx-datetime | 0.7.1 | 0.8.0 | Demo维护性升级，非BLE必需修复 |
| JetBrains AndroidX Lifecycle | 声明2.9.5，Android实际2.9.6 | 2.11.0 | 先去掉未直接使用的声明；确需保留时随Compose对齐 |
| Compose Material3 | `compose.material3`实际1.9.0 | 最新稳定1.9.0 | 保持稳定版本，不随Compose版本机械同步 |
| material-icons-extended | 实际1.7.3 | 1.7.3 | 不再追版本；迁移到Material Symbols资源 |
| Compose Hot Reload | 1.2.0，仅根项目apply false | 1.2.0 | 未应用到任何模块，可移除闲置声明 |
| Foojay resolver convention | 1.0.0 | 1.0.0 | Gradle组织维护、官方文档采用，保持 |
| Ben Manes Versions plugin | 0.54.0 | 0.67.0 | 值得更新；或用GitHub官方Dependabot承担版本更新工作 |

版本来源：本次dependencyUpdates报告、Google/Maven Central/Gradle Plugin Portal元数据，以及以下官方发布说明。更新工具对部分KMP变体报告unresolved，不把工具报告当成完整成功解析或唯一依据。

## 必须优先处理：Gradle安全补丁

[Gradle 8.14.6发布说明](https://docs.gradle.org/8.14.6/release-notes.html)明确列出三项High漏洞；对应公告的受影响范围包含8.14.5，修复版本为8.14.6及9.8.1：

- [GHSA-mvvg-497x-hmj8](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8)：worker/daemon本地通信反序列化风险。
- [GHSA-xwqc-3h47-hg64](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64)：client/daemon认证前反序列化风险。
- [GHSA-j5m7-59rp-24f5](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5)：仓库SSL失败后继续向其他仓库解析可能取到恶意制品。

这是构建主机/供应链风险，不等于已发布BLE库存在远程漏洞。前两项依赖本机loopback访问条件，共享构建机更值得优先处理；不能据此声称手机App可被远程直接利用。

最小变更：更新Wrapper到8.14.6，按官方Wrapper任务更新相关文件并加入对应distributionSha256Sum；复用现有Android、JS、Wasm和iOS Simulator测试。不需要同时修改AGP或业务逻辑。

## 值得更新，但有边界

### Kotlin 2.4.21

[发布说明](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21)包含JS、Wasm、Native工具链修复。项目确实覆盖这些目标，因此值得评估；未发现当前项目必须马上升级编译器才能解决的已确认故障。

[KGP兼容矩阵](https://kotlinlang.org/docs/gradle-configure-project.html)允许Kotlin 2.4.21搭配当前AGP 8.13.2及Gradle 8.14.6，故Kotlin升级可与AGP 9迁移分开。不过本项目是发布型库，应验证外部消费者能否使用新版编译产物。

[Multiplatform兼容表](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)列出的Xcode基线为：Kotlin2.3.21对应26.0，2.4.21对应26.4。本机上一轮实际使用Xcode27.2且测试成功，但不能把一次成功宣称为官方完整支持，也不能保证升级Kotlin就消除所有Xcode27兼容风险。

### Compose 1.12.2不是独立版本替换

[Compose 1.12.2发布说明](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.2)包含iOS TextField失焦/弹出Dialog崩溃及Web焦点修复。移动Demo有输入框和Dialog，但本次未证明它必然触发该崩溃。

[官方版本表](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)表明Compose Multiplatform1.12.2基于Jetpack Compose1.12.1。本次直接读取[Google Maven发布的ui-android-1.12.1.aar](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.12.1/ui-android-1.12.1.aar)内的`META-INF/com/android/build/gradle/aar-metadata.properties`：

```properties
minCompileSdk=37
minAndroidGradlePluginVersion=9.1.0
```

项目当前compileSdk36、AGP8.13.2不满足要求。AGP9的新DSL又不允许当前KMP与`com.android.library`/`com.android.application`同模块组合。因此需要：库迁移`com.android.kotlin.multiplatform.library`；mobileDemo拆出Android应用模块；协调Gradle、Kotlin及compileSdk。依据：[AGP9迁移说明](https://developer.android.com/build/releases/agp-9-0-0-release-notes)、[KMP迁移指南](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#migrate-to-google-s-plugin-for-android-targets)。

不要把所有工具更新为各自最新就当作兼容组合：Kotlin2.4.21官方完整测试范围上限为Gradle9.7.0、AGP9.3.1，而当前最新分别为9.8.2与9.5.0。后续迁移需重新选配及测试；安全补丁可先在8.14.6完成。

另一个陷阱：Compose发布说明列出的Material3是1.12.0-alpha03；Maven元数据目前最新稳定Material3仍为1.9.0。稳定升级方案应显式核对Material3坐标，避免升级框架时意外带入alpha UI库。

### datetime 0.8.0

[官方说明](https://github.com/Kotlin/kotlinx-datetime/releases/tag/v0.8.0)包含日期计算修复和非抛异常解析API，以及TimeZone序列化弃用。本项目仅用于Demo本地时间格式化，且Clock/Instant已经使用`kotlin.time`；这是低优先级维护升级，无需为“官方化”移除官方datetime库。

### Versions插件0.67.0

它是社区构建插件，不进入BLE库或App运行时。旧审计因0.61.0污染Kotlin构建类路径而停留0.54.0，这一结论需要更新：[0.63.0发布说明](https://github.com/ben-manes/gradle-versions-plugin/releases/tag/v0.63.0)已明确移除发布依赖中的kotlin-bom/kotlin-stdlib，且去掉OkHttp并更新Moshi/Okio；[当前0.67.0](https://github.com/ben-manes/gradle-versions-plugin/releases/tag/v0.67.0)值得作为候选。

[迁移文档](https://github.com/ben-manes/gradle-versions-plugin#readme)要求从旧命名空间切换到`io.github.ben-manes.versions`，不要只把原版本字符串改为0.67.0。当前Gradle版本满足其最低要求，但本轮未实际应用新版插件，不能宣称已验证兼容。

## 第三方与官方替代

| 依赖/工具 | 来源与位置 | 是否替换 |
| --- | --- | --- |
| kotlinx-coroutines / kotlinx-datetime / kotlin-test | Kotlin/JetBrains官方；库、Demo、测试 | 保留，没有更合适的“官方替代” |
| AndroidX Activity / Startup / annotation / tracing | Google官方；Android | 保留；Startup已经承担自动初始化，无需手写ContentProvider替换 |
| Compose / org.jetbrains.androidx.lifecycle / Skiko | JetBrains官方多平台组件；Demo | 保留必要部分；不能只看group不是androidx就误判为非官方封装 |
| Foojay resolver | Gradle组织仓库维护；构建期，调用Foojay外部服务 | 已属于官方生态推荐路径；可保持。若本地/CI统一预装匹配JDK可去掉自动下载能力，但要同时处理daemon JVM criteria |
| Ben Manes Versions | 社区；构建期 | 保留则升级候选0.67.0；希望减少社区插件可移除并采用GitHub官方Dependabot，功能并非一对一的Gradle内置替代 |
| material-icons-extended | 官方旧图标产物，Demo | 推荐换为Google Material Symbols按需下载的XML矢量资源，放入composeResources/drawable，不再引入另一套社区图标包装库 |
| @js-joda/core 3.2.0 | 社区，Web Demo传递运行时依赖，由官方datetime引入 | 不手动排除或独立强升；跟随datetime维护。改用原生JS Date需要平台专属实现，未必值得 |
| webpack、Mocha、karma及适配器 | 社区构建/测试工具，由Kotlin插件管理；当前karma来自Kotlin维护分支 | 官方Kotlin/JS工具链本身使用这些工具；优先随KGP更新，不手工改生成package.json或逐个替换 |

依据：

- [JetBrains官方Lifecycle接入文档](https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html)直接推荐`org.jetbrains.androidx.lifecycle`坐标。
- [Gradle官方Toolchains文档](https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories)使用Foojay插件；[插件源码](https://github.com/gradle/foojay-toolchains)在Gradle组织下。
- [Google图标文档](https://developer.android.com/develop/ui/compose/graphics/images/material)已明确旧Material Icon库不再维护/推荐，改用Material Symbols；[KMP说明](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html#implicit-dependency-on-material-icons-core-removed)也给出XML矢量资源路径。
- [Dependabot支持列表](https://docs.github.com/en/code-security/reference/supply-chain-security/supported-ecosystems-and-repositories#gradle)支持Gradle Kotlin DSL、版本目录和Wrapper；版本更新与安全更新不是同一能力，Gradle安全更新依赖额外的依赖图提交，不能把启用版本检查等同完整漏洞扫描。
- [datetime0.8.0官方构建源码](https://github.com/Kotlin/kotlinx-datetime/blob/v0.8.0/core/build.gradle.kts)仍声明`@js-joda/core:3.2.0`。
- [Kotlin/JS官方配置文档](https://kotlinlang.org/docs/js-project-setup.html)说明插件自动管理npm和webpack。

## 可以先做的精简

源码检索未发现两个Demo直接使用`ViewModel`、`collectAsStateWithLifecycle`或其他androidx.lifecycle API。建议删除两个Demo中显式的lifecycle-viewmodel与lifecycle-runtime-compose声明，再编译确认；Compose仍可能传递依赖Lifecycle，不能承诺从最终包完全移除它。

`activity-ktx`当前已由activity-compose传递提供，且没有独立KTX API使用证据，可去掉重复声明。根项目的Compose Hot Reload和kotlin-android只有apply false，当前没有模块应用，都是精简候选。

JitPack是仓库而非库。当前直接依赖与实际运行时坐标没有显式要求JitPack，可验证移除；保留用户自有Maven仓库。不要把仅来自本机缓存的解析成功视为“新机器完全不需要该仓库”的最终证明。

## 有条件必须更新

mobileDemo targetSdk当前34。若只做ADB/内部Demo，不构成此次升级阻塞；若提交Google Play新应用或更新，[截至本次核实的官方政策](https://support.google.com/googleplay/android-developer/answer/11926878)要求API36或更高（2026-08-31起，普通手机应用）。库AAR与Demo应用的发布要求应分开看。

## 建议执行顺序

1. 最小安全升级：Gradle8.14.6，保留现有AGP/Kotlin/Compose，复用现有测试。
2. 精简闲置声明；Material Symbols按需资源替代旧图标包；评估Versions0.67.0或Dependabot。
3. 单独评估Kotlin2.4.21和datetime0.8.0，验证四平台及库消费者。
4. 若需要Compose1.12.2，安排AGP9、Android应用模块拆分和compileSdk37的完整迁移，不混进安全补丁。

## 执行证据与限制

执行并完成：`dependencyUpdates`；库Android releaseRuntimeClasspath、Demo debugRuntimeClasspath解析；源码用途检索；官方文档/仓库元数据交叉核实；Android UI AAR最低SDK/AGP要求检查。

更新报告包含16个unresolved条目（含KMP动态版本变体及工具内部依赖），不是16个已确认构建错误；当前实际运行时依赖树解析成功。DL库不纳入替换结论。生成的Web package.json用于确认npm传递依赖，未修改它们。

原始证据：`/tmp/ble-dependency-updates/report.json`、`/tmp/ble-dependency-updates.log`、`/tmp/ble-dependency-tree.txt`。

本轮没有实际升级或做升级后回归，也没有对所有传递依赖逐个进行CVE/许可证审计。2026-08-10旧审计保留为历史记录，最新版本和升级建议以本报告为准。
