package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.server.config.ApplicationConfig
import no.nav.flaggskipet.infrastructure.config.stringOrEmpty
import java.net.URI

data class EregConfig(
    val baseUrl: URI,
)

fun ApplicationConfig.toEregConfig() = EregConfig(URI(stringOrEmpty("ereg.baseUrl")))
