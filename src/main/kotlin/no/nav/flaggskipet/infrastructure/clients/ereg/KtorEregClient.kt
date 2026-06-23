package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.infrastructure.dagensDato

internal class KtorEregClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
) : EregClient {
    override suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregResult> = coroutineScope {
        organisasjonsnummer.map { orgnummer ->
            async {
                hentNoekkelinfoFor(orgnummer)
            }
        }.awaitAll()
    }

    private suspend fun hentNoekkelinfoFor(organisasjonsnummer: String): EregResult = try {
        val response = httpClient.get {
            url("$baseUrl/v1/organisasjon/$organisasjonsnummer/noekkelinfo")
            parameter("gyldigDato", dagensDato())
            accept(ContentType.Application.Json)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.body<EregNoekkelinfoResponse>()
                EregResult.Funnet(
                    organisasjonsnummer = organisasjonsnummer,
                    organisasjon = body.toOrganisasjon(),
                )
            }

            HttpStatusCode.NotFound -> EregResult.IkkeFunnet(organisasjonsnummer)
            else -> EregResult.Feil(
                organisasjonsnummer = organisasjonsnummer,
                melding = "Ereg svarte med status ${response.status.value}: ${response.bodyAsText()}",
            )
        }
    } catch (error: Throwable) {
        if (error is CancellationException) {
            throw error
        }

        EregResult.Feil(
            organisasjonsnummer = organisasjonsnummer,
            melding = error.message ?: "Ukjent feil ved kall mot Ereg",
        )
    }
}

internal fun createEregHttpClient(): HttpClient = HttpClient(CIO) {
    configureEregHttpClient()
}

internal fun io.ktor.client.HttpClientConfig<*>.configureEregHttpClient() {
    expectSuccess = false

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}

@Serializable
private data class EregNoekkelinfoResponse(
    val adresse: EregAdresseResponse? = null,
)

@Serializable
private data class EregAdresseResponse(
    val type: String? = null,
    val adresselinje1: String? = null,
    val postnummer: String? = null,
    val landkode: String? = null,
    val kommunenummer: String? = null,
)

private fun EregNoekkelinfoResponse.toOrganisasjon(): Organisasjon = Organisasjon(
    adresse = adresse?.toAdresse(),
)

private fun EregAdresseResponse.toAdresse(): Adresse = Adresse(
    type = type,
    adresselinje1 = adresselinje1,
    postnummer = postnummer,
    landkode = landkode,
    kommunenummer = kommunenummer,
)
