import org.gradle.api.file.DuplicatesStrategy

buildscript {
    dependencies {
        classpath(libs.flyway.database.postgresql)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.flyway)
}

group = "no.nav.flaggskipet"
version = "0.1.0"

application {
    mainClass.set("no.nav.flaggskipet.ApplicationKt")
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
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.hikari)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.postgresql)

    constraints {
        testImplementation(libs.commons.compress) {
            because("Testcontainers 1.21.4 pulls commons-compress 1.24.0 transitively")
        }
    }
}

tasks {
    register("printVersion") {
        description = "Print the version of the app"
        doLast {
            println(project.version)
        }
    }

    test {
        useJUnitPlatform()
    }

    named("check") {
        dependsOn("ktlintCheck")
    }

    shadowJar {
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.WARN
        }
        mergeServiceFiles()
        archiveFileName.set("app.jar")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}
