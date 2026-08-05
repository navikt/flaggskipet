package no.nav.flaggskipet.infrastructure.clients.texas

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.resolve
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.infrastructure.config.stringOrEmpty
import java.net.URI

private const val TEXAS_HTTP_CLIENT = "texasHttpClient"

fun DependencyRegistry.texasModule() {
    provide<HttpClient>(TEXAS_HTTP_CLIENT) { createTexasHttpClient() }
        .cleanup(HttpClient::close)
    provide<TexasClient> { TexasClient(httpClient = resolve(TEXAS_HTTP_CLIENT), config = resolve()) }
}

data class TexasConfig(
    val introspectionEndpoint: URI,
)

fun ApplicationConfig.toTexasConfig(): TexasConfig {
    val introspectionEndpoint = stringOrEmpty("texas.introspectionEndpoint").trim()

    check(introspectionEndpoint.isNotBlank()) {
        "Invalid texas configuration: texas.introspectionEndpoint must be set"
    }

    return TexasConfig(URI(introspectionEndpoint))
}

internal fun createTexasHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = true

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}
