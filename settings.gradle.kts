rootProject.name = "DLBluetoothUtil-Project"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository")
        maven("https://jitpack.io")
    }
}

include(":androidApp", ":composeApp", ":library")
project(":library").name = "DLBluetoothUtil"