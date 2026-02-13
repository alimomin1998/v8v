plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

allprojects {
    group = "io.v8v"
    version = "0.1.1"
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

// ═══════════════════════════════════════════════════════════════════════
// Maven Central publishing — shared configuration for library modules
// ═══════════════════════════════════════════════════════════════════════
subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("maven-publish")) {
            extensions.configure<PublishingExtension> {
                // Maven Central via Sonatype OSSRH
                repositories {
                    maven {
                        name = "MavenCentral"
                        url = uri("https://central.sonatype.com/api/v1/publisher")
                        credentials {
                            username =
                                findProperty("sonatype.username") as String?
                                    ?: System.getenv("SONATYPE_USERNAME")
                                    ?: ""
                            password =
                                findProperty("sonatype.password") as String?
                                    ?: System.getenv("SONATYPE_PASSWORD")
                                    ?: ""
                        }
                    }
                }

                publications.withType<MavenPublication> {
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
            }

            // GPG signing — only required for Maven Central, not mavenLocal
            val secretKeyRingFile = findProperty("signing.secretKeyRingFile") as String?
            if (secretKeyRingFile != null && file(secretKeyRingFile).exists()) {
                apply(plugin = "signing")
                extensions.configure<SigningExtension> {
                    sign(extensions.getByType<PublishingExtension>().publications)
                }
            }
        }
    }
}
