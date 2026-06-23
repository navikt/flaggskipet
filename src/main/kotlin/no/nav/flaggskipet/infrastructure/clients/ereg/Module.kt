package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

fun eregModule(config: EregConfig): Module = module {
    single<HttpClient> { createEregHttpClient() }
    single<EregClient> { KtorEregClient(baseUrl = config.baseUrl, httpClient = get()) }
}
