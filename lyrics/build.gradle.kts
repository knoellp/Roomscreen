plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

// Shared fixtures and test vectors (shared/fixtures, shared/testvectors) are on the test classpath as /fixtures/<name> and /testvectors/<name>.
sourceSets.named("test") { resources.srcDir(rootDir.resolve("shared")) }

dependencies {
    // roon-client provides the Logger seam and re-exports coroutines, serialization and OkHttp.
    api(project(":roon-client"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test { useJUnitPlatform() }
