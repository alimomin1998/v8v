pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Kotlin/JS configures Node distribution repositories at project level.
    // Allow project repositories so kotlinNodeJsSetup can resolve Node artifacts.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "v8v"
include(":core")
include(":example-android")
