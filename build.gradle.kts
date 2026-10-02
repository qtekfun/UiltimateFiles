// AGP 9 bundles Kotlin support (built-in Kotlin) and pulls in its own Kotlin Gradle plugin.
// Pin it to the same version as the Compose compiler plugin so both stay in sync.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
