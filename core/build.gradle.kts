import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm()

    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
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

        // With applyDefaultHierarchyTemplate=true, appleMain/iosMain/macosMain
        // are created automatically by the Kotlin plugin with proper platform
        // library support. No manual source set wiring needed.
    }
}

// ─────────────────────────────────────────────────────────────────────
// The Kotlin metadata compiler for intermediate Apple source sets
// cannot resolve platform-specific cinterop APIs (AVAudioSession, etc).
// The actual per-target native compilations work fine. Disable the
// metadata tasks using taskGraph.whenReady (runs after all tasks exist).
// ─────────────────────────────────────────────────────────────────────
gradle.taskGraph.whenReady {
    allTasks.filter {
        it.name == "compileAppleMainKotlinMetadata" ||
        it.name == "compileIosMainKotlinMetadata" ||
        it.name == "compileMacosMainKotlinMetadata" ||
        it.name == "compileNativeMainKotlinMetadata"
    }.forEach { it.enabled = false }
}

android {
    namespace = "io.v8v.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
