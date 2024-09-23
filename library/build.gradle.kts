import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    iosArm64()
    
    sourceSets {
        commonMain.dependencies {
            // 协程
            implementation(libs.kotlinx.coroutines)
            // 通用计算库
            implementation(libs.dl.common)
        }
        androidMain.dependencies {
            // Android
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.ktx)
            // 协程 Android
            implementation(libs.kotlinx.coroutines.android)
            // startup
            implementation(libs.androidx.startup.runtime)
            // 蓝牙通讯
            implementation(libs.androidx.bluetooth)
            // APP通用工具
            implementation(libs.dl.app)
        }
    }
}

android {
    namespace = "com.d10ng.bluetooth"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

