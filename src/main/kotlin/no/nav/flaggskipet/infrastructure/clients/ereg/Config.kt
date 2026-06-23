package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.server.config.ApplicationConfig
import java.net.URI
import no.nav.flaggskipet.infrastructure.config.stringOrEmpty

data class EregConfig(
    val baseUrl: String,
)

fun ApplicationConfig.toEregConfig(): EregConfig {
    val baseUrl = stringOrEmpty("ereg.baseUrl")

    val isValidBaseUrl = runCatching { URI(baseUrl) }
        .map { uri -> uri.scheme != null && uri.host != null }
        .getOrDefault(false)

    check(isValidBaseUrl) {
        "Invalid ereg configuration: ereg.baseUrl must be a valid URL"
    }

    return EregConfig(baseUrl = baseUrl)
}
