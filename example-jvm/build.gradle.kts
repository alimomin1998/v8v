plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("io.v8v.example.jvm.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":connector-mcp"))
    implementation(project(":connector-remote"))
    implementation(libs.kotlinx.coroutines.core)
    // JVM needs an explicit Ktor engine for the connectors
    implementation(libs.ktor.client.okhttp)
}
