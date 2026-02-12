plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
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
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
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
            implementation(libs.ktor.client.okhttp)
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        // Shared Apple source set for iOS + macOS
        val appleMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
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
    namespace = "io.v8v.remote"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
