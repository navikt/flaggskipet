package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.infrastructure.config.stringOrEmpty
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.net.URI

internal val eregHttpClientQualifier = named("eregHttpClient")

fun eregModule(): Module = module {
    single<HttpClient>(eregHttpClientQualifier) { createEregHttpClient(get()) } onClose {
        it?.close()
    }
    single<EregClient> { HttpClientImpl(httpClient = get(eregHttpClientQualifier)) }
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

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}
