plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.knoellp.roomscreen"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.knoellp.roomscreen"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
    }

    // Release key from the environment (config/signing.env via scripts/dock). Without it the debug build keeps the
    // default debug key and the release build is unsigned. Debug and release share one key when it is configured,
    // so that either APK can replace the other with `adb install -r`.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE")?.takeIf { it.isNotBlank() }
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // No R8 in version 1 (see docs/ARCHITECTURE.md section 11).
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            // In the container the uid has no passwd entry, so user.home would be "?" (Robolectric caches jars there).
            all { test ->
                // Always write the screenshots (no Roborazzi Gradle plugin in use).
                test.systemProperty("roborazzi.test.record", "true")
                System.getenv("HOME")?.let { test.systemProperty("user.home", it) } }
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":roon-client"))
    implementation(project(":lyrics"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit4)
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}
