pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    }
}
val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!coreOnly) google()
        mavenCentral()
        if (coreOnly) maven("https://repo1.maven.org/maven2")
    }
}
rootProject.name = "EugeneMarathon"

// The training logic lives in a plain Kotlin module so it can be tested without the Android SDK.
// Pass -PcoreOnly=true to build and test just that module.
include(":core")
if (!coreOnly) {
    include(":app")
}
