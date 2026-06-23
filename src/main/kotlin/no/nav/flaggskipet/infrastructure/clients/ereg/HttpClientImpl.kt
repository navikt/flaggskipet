package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import no.nav.flaggskipet.infrastructure.dagensDato

internal class HttpClientImpl(
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
        val response = httpClient.get("/v1/organisasjon/$organisasjonsnummer/noekkelinfo") {
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

@Serializable
private data class EregNoekkelinfoResponse(
    val adresse: Adresse? = null,
) {
    @Serializable
    data class Adresse(
        val type: String? = null,
        val adresselinje1: String? = null,
        val postnummer: String? = null,
        val landkode: String? = null,
        val kommunenummer: String? = null,
    )
}

private fun EregNoekkelinfoResponse.toOrganisasjon() = Organisasjon(
    adresse = adresse?.let {
        Adresse(
            type = it.type,
            adresselinje1 = it.adresselinje1,
            postnummer = it.postnummer,
            landkode = it.landkode,
            kommunenummer = it.kommunenummer,
        )
    },
)
