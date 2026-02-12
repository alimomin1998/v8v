import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm()

    js(IR) {
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    val xcf = XCFramework("V8VCore")

    iosArm64 {
        binaries.framework {
            baseName = "V8VCore"
            xcf.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "V8VCore"
            xcf.add(this)
        }
    }
    iosX64 {
        binaries.framework {
            baseName = "V8VCore"
            xcf.add(this)
        }
    }
    macosArm64 {
        binaries.framework {
            baseName = "V8VCore"
            xcf.add(this)
        }
    }
    macosX64 {
        binaries.framework {
            baseName = "V8VCore"
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }

        // Shared Apple source set for iOS + macOS
        val appleMain by creating {
            dependsOn(commonMain.get())
        }
        val iosMain by creating { dependsOn(appleMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        val iosX64Main by getting { dependsOn(iosMain) }

        val macosMain by creating { dependsOn(appleMain) }
        val macosArm64Main by getting { dependsOn(macosMain) }
        val macosX64Main by getting { dependsOn(macosMain) }
    }
}

android {
    namespace = "io.v8v.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
