import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.github.D10NGYANG"
version = "0.2.1"

kotlin {
    jvmToolchain(8)
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
        publishLibraryVariants("release")
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
            implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
            // Android
            implementation(libs.androidx.activity.ktx)
            // 协程 Android
            implementation(libs.kotlinx.coroutines.android)
            // startup
            implementation(libs.androidx.startup.runtime)
            // 蓝牙通讯
            api(files("libs/FastBLE-2.3.4.jar"))
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

val bds100MavenUsername: String by project
val bds100MavenPassword: String by project

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

afterEvaluate {
    publishing {
        publications {
            withType(MavenPublication::class) {
                //artifactId = artifactId.replace(project.name, rootProject.name)
                artifact(tasks["javadocJar"])
            }
        }
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