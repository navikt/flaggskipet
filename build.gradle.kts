import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "no.nav.flaggskippet"
version = "0.1.0"

application {
    mainClass.set("no.nav.flaggskippet.ApplicationKt")
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback.classic)

    testImplementation(libs.kotest.runner.junit5)
}

tasks.test {
    useJUnitPlatform()
}

tasks.wrapper {
    gradleVersion = "9.5.1"
    distributionType = Wrapper.DistributionType.BIN
}
