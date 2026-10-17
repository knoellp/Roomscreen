rootProject.name = "roomscreen"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// The Android `app` module is added in a later step.
include(":roon-client", ":roon-cli")
