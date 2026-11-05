package no.nav.flaggskipet.infrastructure.config

import io.ktor.server.config.ApplicationConfig

data class TiltakspakkeConfig(val tiltakspakke2Visible: Boolean)

fun ApplicationConfig.toTiltakspakkeConfig() = TiltakspakkeConfig(
    tiltakspakke2Visible = propertyOrNull("tiltakspakker.tiltakspakke2Visible")?.getString().toBoolean(),
)
