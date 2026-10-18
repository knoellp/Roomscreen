plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

// Shared fixtures and test vectors (shared/fixtures, shared/testvectors) are on the test classpath as /fixtures/<name> and /testvectors/<name>.
sourceSets.named("test") { resources.srcDir(rootDir.resolve("shared")) }

dependencies {
    api(libs.coroutines.core)
    api(libs.serialization.json)
    api(libs.okhttp)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test { useJUnitPlatform() }
