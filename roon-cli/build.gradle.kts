plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":roon-client"))
    implementation(project(":lyrics"))
}

application {
    mainClass.set("io.github.knoellp.roomscreen.cli.MainKt")
    applicationName = "roon-cli"
}
