package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.resolve
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.infrastructure.config.stringOrEmpty
import java.net.URI

private const val EREG_HTTP_CLIENT = "eregHttpClient"
private const val EREG_REQUEST_TIMEOUT_MILLIS = 15_000L
private const val EREG_CONNECT_TIMEOUT_MILLIS = 5_000L
private const val EREG_SOCKET_TIMEOUT_MILLIS = 10_000L

fun DependencyRegistry.eregModule() {
    provide<HttpClient>(EREG_HTTP_CLIENT) { createEregHttpClient(resolve()) }
        .cleanup(HttpClient::close)
    provide<EregClient> { HttpClientImpl(httpClient = resolve(EREG_HTTP_CLIENT)) }
}

data class EregConfig(
    val baseUrl: URI,
)

fun ApplicationConfig.toEregConfig(): EregConfig {
    val baseUrl = stringOrEmpty("ereg.baseUrl").trim()

    check(baseUrl.isNotBlank()) {
        "Invalid ereg configuration: ereg.baseUrl must be set"
    }

    return EregConfig(URI(baseUrl))
}

internal fun createEregHttpClient(config: EregConfig): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    defaultRequest {
        url(config.baseUrl.toString())
    }

    install(HttpTimeout) {
        requestTimeoutMillis = EREG_REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = EREG_CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = EREG_SOCKET_TIMEOUT_MILLIS
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}
