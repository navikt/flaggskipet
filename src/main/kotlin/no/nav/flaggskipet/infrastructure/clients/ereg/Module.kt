package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal val eregHttpClientQualifier = named("eregHttpClient")

fun eregModule(config: EregConfig): Module = module {
    single<HttpClient>(eregHttpClientQualifier) { createEregHttpClient(config) }
    single<EregClient> { HttpClientImpl(httpClient = get(eregHttpClientQualifier)) }
}

internal fun createEregHttpClient(config: EregConfig): HttpClient = HttpClient(CIO) {
    configureEregHttpClient(config)
}

internal fun io.ktor.client.HttpClientConfig<*>.configureEregHttpClient(config: EregConfig) {
    expectSuccess = false
    defaultRequest {
        config.baseUrl
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
