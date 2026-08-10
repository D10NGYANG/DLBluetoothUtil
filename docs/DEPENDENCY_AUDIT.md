# Dependency Audit

Audit date: 2026-08-10

Scope: `lib` runtime dependencies, repository build plugins, and the demo-only Compose dependencies. The conservative upgrades recommended by this audit have now been applied without changing public APIs.

## Executive summary

The repository now uses the fully-supported Gradle 8.14.5, AGP 8.13.2, and Kotlin Gradle Plugin (KGP) 2.3.21 combination. The previous Kotlin 2.2.21 and AGP 8.13.0 pairing was outside JetBrains' fully-supported matrix because KGP 2.2.21 officially supports AGP only through 8.11.1.

Recommended order:

1. Applied: Gradle 8.14.5, AGP 8.13.2, and Kotlin 2.3.21. AGP 8.13.2 explicitly ships R8 8.13.19 with Kotlin 2.3 support, while KGP 2.3.21 fully supports both AGP 8.13 and Gradle 8.14.
2. Applied: Coroutines 1.11.0, Compose Multiplatform 1.11.1, Compose Hot Reload 1.2.0, and AndroidX Activity 1.13.0 for the demos. This picks up Coroutines fixes directly relevant to this library's heavy `SharedFlow`/`StateFlow` usage without moving to Kotlin 2.4/AGP 9 at the same time.
3. Do not upgrade AGP to 9.x or Gradle to 9.x as a version-only change. First migrate the KMP Android target from `com.android.library` plus `androidTarget {}` to `com.android.kotlin.multiplatform.library` plus `android {}` and clear Gradle 9 deprecations.
4. Keep Startup 1.2.0 and DLLogUtil 0.2.1. DLAppUtil has been removed from `lib`; Android permission checks, location state, and the Bluetooth enable Intent now use platform APIs behind an internal module.

## Current versions and disposition

| Component | Current | Latest stable observed | Disposition |
| --- | ---: | ---: | --- |
| Gradle Wrapper | 8.14.5 | 9.7.0 overall; 8.14.5 in current line | Applied; keep 9.x for AGP 9/KMP migration |
| AGP | 8.13.2 | 9.3.1; 8.13.2 in current line | Applied; do not jump to 9.x |
| Kotlin/KMP | 2.3.21 | 2.4.10 | Applied conservative supported version |
| Compose Multiplatform | 1.11.1 | 1.11.1 | Applied; demo-only |
| Compose Hot Reload | 1.2.0 | 1.2.0 stable | Applied; development tooling only |
| kotlinx-coroutines | 1.11.0 | 1.11.0 | Applied; verify all targets and Android R8 consumer |
| AndroidX Activity | Not used by `lib`; demo uses 1.13.0 | 1.13.0 | Applied to demo; no BLE runtime effect |
| AndroidX Core | Not used directly by `lib` | 1.19.0 | Do not add solely to replace removed utilities |
| AndroidX Startup | 1.2.0 | 1.2.0 | Keep |
| DLAppUtil | Removed | 2.7.1 | No longer present on the library runtime classpath |
| DLLogUtil | 0.2.1 | 0.2.1 | Keep |
| Foojay resolver | 1.0.0 | 1.0.0 | Keep |
| Ben Manes Versions plugin | 0.54.0 | 0.61.0 | Applied compatible upgrade; 0.61.0 injects Kotlin 2.4.10 into the build classpath and conflicts with KGP 2.3.21 |
| `maven-publish` | Gradle built-in | Follows Gradle | No independent version |

The “latest stable observed” values come from the repository's `dependencyUpdates` task and the official sources cited below. Previews were excluded.

## Compatibility findings

### Current AGP, Gradle, Java, and Kotlin

- AGP 8.13 requires Gradle 8.13 or newer and JDK 17 or newer. The repository's Gradle 8.14.5 and Java 21 satisfy these requirements. AGP 8.13 supports compile SDK through API 36.1, so the current compile SDK 36 is valid. [AGP 8.13 release notes](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes)
- Gradle supports running on Java 21 from Gradle 8.5 onward. The current Gradle 8.14.5/Java 21 pair is supported. [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- Gradle's official version service identifies 8.14.5 as the newest patch in the 8.14 line. Moving from 8.14.3 to 8.14.5 keeps the same compatibility envelope while taking patch fixes. [Gradle version service](https://services.gradle.org/versions/all)
- The previous KGP 2.2.21 toolchain supported Gradle 7.6.3-8.14 but fully supported AGP only through 8.11.1. Its pairing with AGP 8.13.0 was outside that tested range and motivated the coordinated toolchain upgrade. [Kotlin Gradle plugin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin)
- KGP 2.3.20-2.3.21 fully supports Gradle 7.6.3-9.3.0 and AGP 8.2.2-9.0.0. Kotlin 2.3.21 with AGP 8.13.2 and Gradle 8.14.5 is therefore a fully-supported combination, and AGP 8.13.2 explicitly updates R8 for Kotlin 2.3 support. [Kotlin Gradle plugin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin) and [AGP 8.13 release notes](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes)
- Kotlin 2.4.10 is the latest stable release, but it is not required to resolve the current compatibility gap. Keeping that major/minor step separate lowers the blast radius across Android, Native, JS, Wasm, and Compose. [Kotlin 2.4.10 release](https://github.com/JetBrains/kotlin/releases/tag/v2.4.10)

### Why not AGP 9 / Gradle 9 yet

AGP 9.3.1 requires Gradle 9.5.0 and JDK 17 or newer. That is a coordinated major upgrade, not an isolated plugin bump. [AGP 9.3 release notes](https://developer.android.com/build/releases/gradle-plugin)

The repository still applies `com.android.library` beside `org.jetbrains.kotlin.multiplatform` and defines `androidTarget {}`. JetBrains now recommends the dedicated `com.android.kotlin.multiplatform.library` plugin and `android {}` target; Kotlin 2.3 introduced this migration path and its deprecation cycle. Perform that migration and its publication/variant validation before AGP 9. [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#migrate-to-google-s-plugin-for-android-targets)

The Gradle scan also reports deprecated features that are incompatible with Gradle 9.0. Before the upgrade, the `dependencyUpdates` task produced configuration-cache violations under plugin 0.53.0. These do not break the library build today, but they are additional reasons not to move the wrapper to Gradle 9 before a dedicated migration.

## Runtime dependency findings

### kotlinx-coroutines 1.11.0: applied

This is the upgrade with the clearest potential runtime benefit. The library uses `MutableSharedFlow`, `MutableStateFlow`, cancellation, and Flow-based callback routing throughout Android, iOS, JS, and Wasm.

Coroutines 1.11.0 fixes:

- a `SharedFlow` invalid state when a subscriber and emitter are cancelled simultaneously;
- an R8 optimization that could allow `shareIn`/`stateIn` coroutines to be garbage-collected;
- incorrect `flowOn` handling of `ThreadContextElement` updates.

The first two are relevant to a BLE library with concurrent callback emission and release builds processed by R8. The release also contains JS/Wasm breaking changes around Promise helpers and exception reporting, so upgrade only with Android minified tests plus JS/Wasm browser tests. [kotlinx.coroutines 1.11.0 release](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0)

### Kotlin 2.3.21: applied conservative target

Besides restoring the supported KGP/AGP combination, Kotlin 2.3.21 contains fixes across Wasm klib/incremental compilation, JS incremental compilation, Kotlin/Native Objective-C interop, and KMP Android dependency inference. Those areas match this library's Android/iOS/JS/Wasm target set more closely than Kotlin 2.4-specific features. [Kotlin 2.3.21 release](https://github.com/JetBrains/kotlin/releases/tag/v2.3.21)

### AndroidX Activity and Core: removed from the library

`lib` no longer declares `activity-ktx` or imports AndroidX Core. Permission checks use `Context.checkSelfPermission`, which is available above this project's min SDK, while the demo retains Activity Compose/Activity KTX for its UI and Activity Result permission flow.

Activity 1.13.0's documented changes concern picture-in-picture, edge-to-edge, back handling, and photo/video contracts, not BLE, permission requests, or GATT stability. Upgrading the demo can therefore remain part of the Compose/tooling upgrade rather than the BLE library change. [AndroidX Activity release notes](https://developer.android.com/jetpack/androidx/releases/activity)

Core 1.19.0 is current, but no Core release note reviewed here identifies a BLE GATT fix and the library does not need the artifact after the platform-API replacement. [AndroidX Core release notes](https://developer.android.com/jetpack/androidx/releases/core)

### AndroidX Startup 1.2.0: keep

1.2.0 is current. It fixes secondary-process metadata lookup and `AppInitializer.isEagerlyInitialized()` and includes baseline-profile support. There is no upgrade available. [AndroidX Startup release notes](https://developer.android.com/jetpack/androidx/releases/startup)

### DLAppUtil 2.7.1: removed

The configured first-party Maven repository identifies 2.7.1 as both latest and release. [DLAppUtil Maven metadata](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLAppUtil/maven-metadata.xml)

The audit originally found that `lib` used only `ActivityManager`, `PermissionManager`, and `isLocationEnabled`, while DLAppUtil's unrelated `NotificationController` caused a `NotificationPermission` Lint failure in the demo. Those calls have now been replaced with an internal `AndroidBleEnvironment` module using Android platform APIs. Runtime permission requests are owned by the caller, demonstrated by `mobileDemo`; the library performs defensive permission checks before each protected operation. Root `check`, Android demo Lint/assembly, and iOS compilation pass, and `dependencyInsight` finds no DLAppUtil on `releaseRuntimeClasspath`.

### DLLogUtil 0.2.1: keep

The configured first-party Maven repository identifies 0.2.1 as both latest and release. [DLLogUtil Maven metadata](https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository/com/github/D10NGYANG/DLLogUtil/maven-metadata.xml)

No newer release or dependency-related BLE fix is available. Keep it while preserving the current full HEX logging behavior.

There is a publication/API declaration issue independent of the version. `BluetoothManagerLog` is public and its inferred type comes from DLLogUtil, but `commonMain` declares DLLogUtil with `implementation`. The generated platform POM therefore exposes it only at runtime, while callers that compile against the logger type must add DLLogUtil themselves as documented in the README. Either change the dependency to `api` so publication metadata matches the public API, or introduce a library-owned logging facade that does not expose DLLogUtil types. Treat this as an explicit API/publication decision and validate a minimal external consumer before the next release.

## Demo and build-only dependencies

- Compose Multiplatform 1.11.1 is current and supports Android 5+, iOS 14+, and WasmGC browsers. JetBrains states that the latest Compose release is compatible with the latest Kotlin and recommends at least Kotlin 2.2.20 for rapidly evolving iOS/web targets. Because Compose is used only by `mobileDemo` and `webDemo`, this upgrade cannot improve `lib` BLE runtime performance directly. Upgrade it with Kotlin to keep demo compilation representative. [Compose compatibility and versions](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html) and [Compose Multiplatform 1.11.1 release](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.11.1)
- Compose Hot Reload has moved from the 1.0.0-rc02 preview to stable 1.2.0. It is build/development tooling only and is not applied to `lib`.
- Ben Manes Versions plugin 0.54.0 fixes dynamic configuration handling without injecting the Kotlin 2.4.10 BOM used by 0.61.0. Testing 0.61.0 with KGP 2.3.21 resolved `kotlin-daemon-client` to 2.4.10 and caused a compiler `NoSuchMethodError`, so 0.54.0 is the compatible upgrade for this toolchain. [Versions plugin 0.54.0 release](https://github.com/ben-manes/gradle-versions-plugin/releases/tag/v0.54.0)
- Compose's generated `compose.runtime`, `compose.foundation`, `compose.material3`, and related dependency accessors, together with the common demo's JetBrains `Preview` annotation, currently emit deprecation warnings. They remain in place because the plugin maps components to independently released artifacts across Android and iOS; replacing every accessor with the plugin version as a direct artifact version is invalid (for example, `material3:1.11.1` does not exist). Migrate these APIs separately when Compose publishes a cross-platform replacement path.
- Foojay resolver 1.0.0 is current in the Gradle Plugin Portal. [Plugin Portal metadata](https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/maven-metadata.xml)
- `maven-publish` is a Gradle core plugin and follows the wrapper version; there is no separate version to upgrade.

## Completed verification

The Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.3.21 / Coroutines 1.11.0 / Compose 1.11.1 set passed:

```text
./gradlew clean check
./gradlew :DLBluetoothUtil:compileKotlinIosArm64
./gradlew :DLBluetoothUtil:compileTestKotlinIosArm64
./gradlew :mobileDemo:lintDebug
./gradlew :mobileDemo:assembleDebug
./gradlew :mobileDemo:compileKotlinIosArm64
```

Root `check` includes the library Android Lint/unit tests and JS/Wasm browser tests. The demo Android APK and iOS target also compile successfully. A minified external Android consumer and published KMP metadata/AAR inspection remain recommended release checks because the relevant Coroutines fix involves R8 and this repository does not contain an external publication consumer fixture.

## Local evidence collected

- `./gradlew --version`: Gradle 8.14.5, launcher Java 21.0.10, daemon toolchain Java 21.
- `./gradlew dependencyUpdates --refresh-dependencies`: completed successfully and produced `build/dependencyUpdates/report.txt`; it also exposed configuration-cache incompatibilities in the reporting task/plugin.
- `./gradlew :DLBluetoothUtil:dependencies --configuration androidDebugCompileClasspath`: confirmed direct and transitive Android dependency versions.
- `./gradlew clean check :mobileDemo:lintDebug :mobileDemo:assembleDebug :DLBluetoothUtil:compileKotlinIosArm64 :DLBluetoothUtil:compileTestKotlinIosArm64 :mobileDemo:compileKotlinIosArm64`: passed 246 actionable tasks after the upgrades; the previous demo `NotificationPermission` failure is gone.
- `./gradlew :DLBluetoothUtil:dependencyInsight --dependency DLAppUtil --configuration releaseRuntimeClasspath`: no matching dependency found.
- `./gradlew :DLBluetoothUtil:dependencyInsight --dependency kotlinx-coroutines-core --configuration releaseRuntimeClasspath`: resolves the direct dependency and DLLogUtil's 1.10.2 request to 1.11.0.
