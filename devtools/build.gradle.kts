import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "no.nav.flaggskipet"
version = "0.1.0"

application {
    mainClass.set("no.nav.flaggskipet.devtools.kafka.SykmeldingProducerKt")
}

kotlin {
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )
}

dependencies {
    implementation(libs.kafka.clients)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
}

tasks.register<JavaExec>("runKafkaSykmeldingProducer") {
    description = "Send a sykmelding message to local Kafka"
    group = "devtools"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("no.nav.flaggskipet.devtools.kafka.SykmeldingProducerKt")
}
