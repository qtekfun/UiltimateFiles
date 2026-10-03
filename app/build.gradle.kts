plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The version lives in one place, `appVersion` in gradle.properties (SemVer, optionally `-rc.N`). The version code is
 * derived, never set by hand: (MAJOR*10000 + MINOR*100 + PATCH) * 100 + N, with N = 99 for a final release, so a final
 * version always sorts after its release candidates and nothing depends on the date or the machine (reproducible builds).
 */
val appVersion = providers.gradleProperty("appVersion").get()

fun versionCodeOf(version: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?$""").matchEntire(version)
        ?: error("appVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N: $version")
    val (major, minor, patch, rc) = match.destructured
    val release = if (rc.isEmpty()) 99 else rc.toInt().also { require(it in 1..98) { "rc number must be 1..98" } }
    return (major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()) * 100 + release
}

// Master builds are published as "nightly": a different application id, so they install next to the official release
// instead of racing its version code: a nightly's code is just the workflow run number. Official releases are built
// without this flag.
val nightly = (findProperty("nightly") as String?) == "true"
val nightlyRun = (findProperty("nightlyRun") as String?)?.toIntOrNull() ?: 0

// Signing material comes from the environment so no secret ever lives in the repo.
val signingKeystore: String? = System.getenv("UF_KEYSTORE_FILE")

android {
    namespace = "com.qtekfun.ultimatefiles"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qtekfun.ultimatefiles"
        minSdk = 26
        targetSdk = 35
        versionCode = if (nightly) nightlyRun else versionCodeOf(appVersion)
        versionName = if (nightly) "$appVersion-nightly.$nightlyRun" else appVersion
        if (nightly) applicationIdSuffix = ".nightly"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = System.getenv("UF_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UF_KEY_ALIAS")
                keyPassword = System.getenv("UF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release keystore the APK stays unsigned (what F-Droid builds and then re-signs or verifies),
            // unless -PdebugSigning=true asks for an installable build signed with the debug key (nightlies).
            signingConfig = when {
                signingKeystore != null -> signingConfigs.getByName("release")
                (findProperty("debugSigning") as String?) == "true" -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // F-Droid: no dependency metadata blob signed for Google.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.xz) // LZMA for 7z
    implementation(libs.sshj)
    implementation(libs.smbj)
    implementation(libs.slf4j.nop) // sshj logs through SLF4J; nothing is written anywhere

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // the Android stub of org.json does nothing on the JVM
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sshd.core)
    testImplementation(libs.sshd.sftp)
    testImplementation(libs.slf4j.nop)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
