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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.domain.dagensDato
import no.nav.flaggskipet.domain.vurdering.Adresse

internal class HttpClientImpl(
    private val httpClient: HttpClient,
    maksParallelleKall: Int = MAKS_PARALLELLE_EREG_KALL,
    maksBatchVarighetMillis: Long = MAKS_BATCH_VARIGHET_MILLIS,
) : EregClient {
    private val samtidighetsbegrenser = Semaphore(
        maksParallelleKall.also { require(it > 0) { "maksParallelleKall must be positive" } },
    )
    private val maksBatchVarighetMillis = maksBatchVarighetMillis.also {
        require(it > 0) { "maksBatchVarighetMillis must be positive" }
    }

    override suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregNoekkelinfo> = withTimeout(maksBatchVarighetMillis) {
        organisasjonsnummer.map { orgnummer ->
            async {
                samtidighetsbegrenser.withPermit {
                    hentNoekkelinfoFor(orgnummer)
                }
            }
        }.awaitAll()
    }

    private suspend fun hentNoekkelinfoFor(
        organisasjonsnummer: String,
    ): EregNoekkelinfo {
        val response = httpClient.get("/v2/organisasjon/$organisasjonsnummer/noekkelinfo") {
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

private const val MAKS_PARALLELLE_EREG_KALL = 10
private const val MAKS_BATCH_VARIGHET_MILLIS = 30_000L

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
