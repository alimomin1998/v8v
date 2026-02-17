plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

allprojects {
    group = "io.github.alimomin1998"
    version = "0.3.0"
}

// ═══════════════════════════════════════════════════════════════════════
// ktlint — consistent code formatting across all modules
// ═══════════════════════════════════════════════════════════════════════
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }
}


