package no.nav.flaggskipet.infrastructure.clients.ereg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import no.nav.flaggskipet.domain.vurdering.Adresse
import no.nav.flaggskipet.infrastructure.dagensDato

internal class HttpClientImpl(
    private val httpClient: HttpClient,
) : EregClient {
    override suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregNoekkelinfo> = coroutineScope {
        organisasjonsnummer.map { orgnummer ->
            async {
                hentNoekkelinfoFor(orgnummer)
            }
        }.awaitAll()
    }

    private suspend fun hentNoekkelinfoFor(
        organisasjonsnummer: String,
    ): EregNoekkelinfo {
        val response = httpClient.get("/v1/organisasjon/$organisasjonsnummer/noekkelinfo") {
            parameter("gyldigDato", dagensDato())
            accept(ContentType.Application.Json)
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.body<EregNoekkelinfoResponse>()
                EregNoekkelinfo(
                    organisasjonsnummer = organisasjonsnummer,
                    adresse = Adresse(
                        type = body.adresse.type,
                        postnummer = body.adresse.postnummer,
                        kommunenummer = body.adresse.kommunenummer,
                    ),
                )
            }

            HttpStatusCode.NotFound ->
                EregNoekkelinfo(organisasjonsnummer = organisasjonsnummer, adresse = null)

            else -> throw IllegalStateException(
                "Ereg responded with ${response.status.value}: ${response.bodyAsText()}",
            )
        }
    }
}

@Serializable
private data class EregNoekkelinfoResponse(
    val adresse: Adresse = Adresse(),
) {
    @Serializable
    data class Adresse(
        val type: String = "",
        val postnummer: String = "",
        val kommunenummer: String = "",
    )
}
