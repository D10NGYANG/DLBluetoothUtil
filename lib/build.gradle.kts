import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.github.D10NGYANG"
version = "0.5.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
        publishLibraryVariants("release")
    }
    iosArm64()
    
    sourceSets {
        commonMain.dependencies {
            // 协程
            implementation(libs.kotlinx.coroutines)
        }
        androidMain.dependencies {
            // Android
            implementation(libs.androidx.activity.ktx)
            // 协程 Android
            implementation(libs.kotlinx.coroutines.android)
            // startup
            implementation(libs.androidx.startup.runtime)
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

val bds100MavenUsername: String by project
val bds100MavenPassword: String by project

afterEvaluate {
    publishing {
        repositories {
            maven {
                url = uri("/Users/d10ng/project/kotlin/maven-repo/repository")
            }
            maven {
                credentials {
                    username = bds100MavenUsername
                    password = bds100MavenPassword
                }
                setUrl("https://nexus.bds100.com/repository/maven-releases/")
            }
        }
    }
}