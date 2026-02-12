plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "io.v8v"
    version = "0.1.0"
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
                        url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                        credentials {
                            username = findProperty("sonatype.username") as String? ?: System.getenv("SONATYPE_USERNAME") ?: ""
                            password = findProperty("sonatype.password") as String? ?: System.getenv("SONATYPE_PASSWORD") ?: ""
                        }
                    }
                }

                publications.withType<MavenPublication> {
                    pom {
                        name.set("V8V - ${project.name}")
                        description.set("Cross-platform voice orchestration framework with native on-device STT")
                        url.set("https://github.com/AliHaider-codes/v8v")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                        developers {
                            developer {
                                id.set("alihaider")
                                name.set("Ali Haider")
                            }
                        }
                        scm {
                            url.set("https://github.com/AliHaider-codes/v8v")
                            connection.set("scm:git:git://github.com/AliHaider-codes/v8v.git")
                            developerConnection.set("scm:git:ssh://git@github.com/AliHaider-codes/v8v.git")
                        }
                    }
                }
            }

            // GPG signing (only when credentials are available)
            if (findProperty("signing.keyId") != null || System.getenv("GPG_KEY_ID") != null) {
                apply(plugin = "signing")
                extensions.configure<SigningExtension> {
                    sign(extensions.getByType<PublishingExtension>().publications)
                }
            }
        }
    }
}
