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
import no.nav.flaggskipet.domain.assessment.Adresse
import no.nav.flaggskipet.domain.today

internal class HttpClientImpl(
    private val httpClient: HttpClient,
    maxParallelCalls: Int = MAX_PARALLEL_EREG_CALLS,
    maxBatchDurationMillis: Long = MAX_BATCH_DURATION_MILLIS,
) : EregClient {
    private val concurrencyLimiter = Semaphore(
        maxParallelCalls.also { require(it > 0) { "maxParallelCalls must be positive" } },
    )
    private val maxBatchDurationMillis = maxBatchDurationMillis.also {
        require(it > 0) { "maxBatchDurationMillis must be positive" }
    }

    override suspend fun getNoekkelinfo(orgnummer: List<String>): List<EregNoekkelinfo> = withTimeout(maxBatchDurationMillis) {
        orgnummer.map { orgnummer ->
            async {
                concurrencyLimiter.withPermit {
                    getNoekkelinfoFor(orgnummer)
                }
            }
        }.awaitAll()
    }

    private suspend fun getNoekkelinfoFor(
        orgnummer: String,
    ): EregNoekkelinfo {
        val response = httpClient.get("/v2/organisasjon/$orgnummer/noekkelinfo") {
            parameter("gyldigDato", today())
            accept(ContentType.Application.Json)
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.body<EregNoekkelinfoResponse>()
                EregNoekkelinfo(
                    orgnummer = orgnummer,
                    adresse = Adresse(
                        type = body.adresse.type,
                        postnummer = body.adresse.postnummer,
                        kommunenummer = body.adresse.kommunenummer,
                    ),
                )
            }

            HttpStatusCode.NotFound ->
                EregNoekkelinfo(orgnummer = orgnummer, adresse = null)

            else -> throw IllegalStateException(
                "Ereg responded with ${response.status.value}: ${response.bodyAsText()}",
            )
        }
    }
}

private const val MAX_PARALLEL_EREG_CALLS = 10
private const val MAX_BATCH_DURATION_MILLIS = 30_000L

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
