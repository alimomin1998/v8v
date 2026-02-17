import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("com.vanniktech.maven.publish")
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        // With applyDefaultHierarchyTemplate=true, appleMain/iosMain/macosMain
        // are created automatically by the Kotlin plugin with proper platform
        // library support. No manual source set wiring needed.
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// The Kotlin metadata compiler for intermediate Apple source sets
// cannot resolve platform-specific cinterop APIs (AVAudioSession, etc).
// The actual per-target native compilations work fine. Disable the
// metadata tasks using taskGraph.whenReady (runs after all tasks exist).
// ─────────────────────────────────────────────────────────────────────
gradle.taskGraph.whenReady {
    val metadataTasks =
        allTasks.filter {
            it.name == "compileAppleMainKotlinMetadata" ||
                it.name == "compileIosMainKotlinMetadata" ||
                it.name == "compileMacosMainKotlinMetadata" ||
                it.name == "compileNativeMainKotlinMetadata"
        }
    metadataTasks.forEach { it.enabled = false }
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

mavenPublishing {
    pom {
        name.set("V8V - ${project.name}")
        description.set("Cross-platform voice orchestration framework with native on-device STT")
        url.set("https://github.com/alimomin1998/v8v")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("alimomin")
                name.set("Ali Momin")
            }
        }
        scm {
            url.set("https://github.com/alimomin1998/v8v")
            connection.set("scm:git:git://github.com/alimomin1998/v8v.git")
            developerConnection.set("scm:git:ssh://git@github.com/alimomin1998/v8v.git")
        }
    }
}
