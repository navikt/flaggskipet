import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
}

group = "no.nav.flaggskippet"
version = "0.1.0"

application {
    mainClass.set("no.nav.flaggskippet.ApplicationKt")
}

kotlin {
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.kotest.runner.junit5)
}

tasks {
    register("printVersion") {
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
