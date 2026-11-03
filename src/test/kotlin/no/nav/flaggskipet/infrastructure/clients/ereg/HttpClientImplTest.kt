package no.nav.flaggskipet.infrastructure.clients.ereg

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.domain.assessment.Adresse
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

class HttpClientImplTest :
    FunSpec({
        test("getNoekkelinfo maps 200 to EregNoekkelinfo with Adresse and sends gyldigDato for today") {
            val mockEngine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.protocol shouldBe URLProtocol.HTTPS
                request.url.host shouldBe "ereg-services.dev.intern.nav.no"
                request.url.encodedPath shouldBe "/v2/organisasjon/313644480/noekkelinfo"
                request.url.parameters["gyldigDato"]?.matches(Regex("""\d{4}-\d{2}-\d{2}""")) shouldBe true

                respondJson(
                    """
                    {
                      "organisasjonsnummer": "313644480",
                      "navn": { "navnelinje1": "KJEMPENDE SKEPTISK KATT FRYNSE" },
                      "adresse": {
                        "type": "Forretningsadresse",
                        "adresselinje1": "Ottars veg 8 A",
                        "postnummer": "9012",
                        "landkode": "NO",
                        "kommunenummer": "5401"
                      }
                    }
                    """.trimIndent(),
                )
            }
            val client = HttpClientImpl(
                httpClient = createHttpClient(mockEngine),
            )

            client.getNoekkelinfo(listOf("313644480")) shouldBe listOf(
                EregNoekkelinfo(
                    orgnummer = "313644480",
                    adresse = Adresse(
                        type = "Forretningsadresse",
                        postnummer = "9012",
                        kommunenummer = "5401",
                    ),
                ),
            )
        }

        test("getNoekkelinfo maps 404 to EregNoekkelinfo with null Adresse") {
            val client = HttpClientImpl(
                httpClient = createHttpClient(
                    MockEngine {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.NotFound,
                            headers = emptyJsonHeaders(),
                        )
                    },
                ),
            )

            client.getNoekkelinfo(listOf("999999999")) shouldBe listOf(
                EregNoekkelinfo(orgnummer = "999999999", adresse = null),
            )
        }

        test("getNoekkelinfo throws when Ereg fails") {
            val client = HttpClientImpl(
                httpClient = createHttpClient(
                    MockEngine {
                        respond(
                            content = ByteReadChannel("""{"message":"boom"}"""),
                            status = HttpStatusCode.InternalServerError,
                            headers = emptyJsonHeaders(),
                        )
                    },
                ),
            )

            assertThrows<RuntimeException> { client.getNoekkelinfo(listOf("111111111")) }
        }

        test("cancels the entire batch when the deadline expires") {
            val client = HttpClientImpl(
                httpClient = createHttpClient(
                    MockEngine {
                        delay(5_000)
                        respondJson("""{"adresse":{}}""")
                    },
                ),
                maxParallelCalls = 1,
                maxBatchDurationMillis = 100,
            )

            assertThrows<TimeoutCancellationException> {
                client.getNoekkelinfo(uniqueOrgnumre(2, start = 0))
            }
        }

        test("limits parallel Ereg calls across concurrent batches") {
            val activeCalls = AtomicInteger(0)
            val maxActiveCalls = AtomicInteger(0)
            val threeCallsStarted = CompletableDeferred<Unit>()
            val mockEngine = MockEngine {
                val active = activeCalls.incrementAndGet()
                maxActiveCalls.updateAndGet { previousMax -> maxOf(previousMax, active) }
                if (active == 3) threeCallsStarted.complete(Unit)
                try {
                    threeCallsStarted.await()
                    respondJson("""{"adresse":{}}""")
                } finally {
                    activeCalls.decrementAndGet()
                }
            }
            val client = HttpClientImpl(
                httpClient = createHttpClient(mockEngine),
                maxParallelCalls = 3,
            )

            coroutineScope {
                listOf(
                    async { client.getNoekkelinfo(uniqueOrgnumre(10, start = 0)) },
                    async { client.getNoekkelinfo(uniqueOrgnumre(10, start = 10)) },
                ).awaitAll()
            }

            maxActiveCalls.get() shouldBe 3
        }
    })

private fun uniqueOrgnumre(count: Int, start: Int): List<String> = List(count) { index -> "31364%04d".format(start + index) }

private fun createHttpClient(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
    expectSuccess = false
    defaultRequest {
        url("https://ereg-services.dev.intern.nav.no")
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

private fun emptyJsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun MockRequestHandleScope.respondJson(json: String) = respond(
    content = ByteReadChannel(json),
    status = HttpStatusCode.OK,
    headers = emptyJsonHeaders(),
)
