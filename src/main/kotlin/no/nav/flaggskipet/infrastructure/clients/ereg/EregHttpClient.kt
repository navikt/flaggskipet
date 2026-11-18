package no.nav.flaggskipet.infrastructure.clients.ereg

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregError
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.domain.assessment.Adresse
import no.nav.flaggskipet.domain.today
import kotlin.time.Duration.Companion.milliseconds

internal class EregHttpClient(
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

    override suspend fun getNoekkelinfo(orgnummer: List<String>) = either {
        val results = withTimeoutOrNull(maxBatchDurationMillis.milliseconds) {
            orgnummer.map { nr ->
                async { concurrencyLimiter.withPermit { getNoekkelinfoFor(nr) } }
            }.awaitAll()
        } ?: raise(EregError.Timeout)

        results.bindAll()
    }

    private suspend fun getNoekkelinfoFor(
        orgnummer: String,
    ): Either<EregError, EregNoekkelinfo> = either {
        catch({
            val response = httpClient.get("/v2/organisasjon/$orgnummer/noekkelinfo") {
                parameter("gyldigDato", today())
                accept(ContentType.Application.Json)
            }

            when (response.status) {
                HttpStatusCode.OK -> response.body<EregNoekkelinfoResponse>().toDomain(orgnummer)
                HttpStatusCode.NotFound -> EregNoekkelinfo(orgnummer = orgnummer, adresse = null)
                else -> raise(EregError.UnexpectedStatus(response.status.value))
            }
        }) { raise(EregError.RequestFailed) }
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

private fun EregNoekkelinfoResponse.toDomain(orgnummer: String) = EregNoekkelinfo(
    orgnummer = orgnummer,
    adresse = Adresse(
        type = adresse.type,
        postnummer = adresse.postnummer,
        kommunenummer = adresse.kommunenummer,
    ),
)
