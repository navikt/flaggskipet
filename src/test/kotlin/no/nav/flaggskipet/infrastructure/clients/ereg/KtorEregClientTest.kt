package no.nav.flaggskipet.infrastructure.clients.ereg

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

class KtorEregClientTest :
    FunSpec({
        test("hentNoekkelinfo maps 200 to Funnet and sends gyldigDato for today in Oslo") {
            val mockEngine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.encodedPath shouldBe "/v1/organisasjon/313644480/noekkelinfo"
                request.url.parameters["gyldigDato"] shouldBe "2026-06-24"

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

            val client = KtorEregClient(
                baseUrl = "https://ereg-services.dev.intern.nav.no",
                httpClient = createHttpClient(mockEngine),
                gyldigDatoProvider = { "2026-06-24" },
            )

            client.hentNoekkelinfo(listOf("313644480")) shouldBe listOf(
                EregResult.Funnet(
                    organisasjonsnummer = "313644480",
                    organisasjon = Organisasjon(
                        adresse = Adresse(
                            type = "Forretningsadresse",
                            adresselinje1 = "Ottars veg 8 A",
                            postnummer = "9012",
                            landkode = "NO",
                            kommunenummer = "5401",
                        ),
                    ),
                ),
            )
        }

        test("hentNoekkelinfo maps 404 to IkkeFunnet") {
            val client = KtorEregClient(
                baseUrl = "https://ereg-services.dev.intern.nav.no",
                httpClient = createHttpClient(
                    MockEngine {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.NotFound,
                            headers = emptyJsonHeaders(),
                        )
                    },
                ),
                gyldigDatoProvider = { "2026-06-24" },
            )

            client.hentNoekkelinfo(listOf("999999999")) shouldBe listOf(
                EregResult.IkkeFunnet("999999999"),
            )
        }

        test("hentNoekkelinfo maps non-404 failures to Feil") {
            val client = KtorEregClient(
                baseUrl = "https://ereg-services.dev.intern.nav.no",
                httpClient = createHttpClient(
                    MockEngine {
                        respond(
                            content = ByteReadChannel("""{"message":"boom"}"""),
                            status = HttpStatusCode.InternalServerError,
                            headers = emptyJsonHeaders(),
                        )
                    },
                ),
                gyldigDatoProvider = { "2026-06-24" },
            )

            client.hentNoekkelinfo(listOf("111111111")) shouldBe listOf(
                EregResult.Feil(
                    organisasjonsnummer = "111111111",
                    melding = """Ereg svarte med status 500: {"message":"boom"}""",
                ),
            )
        }

        test("hentNoekkelinfo returns one result per input and performs requests in parallel") {
            val activeRequests = AtomicInteger(0)
            val maxConcurrentRequests = AtomicInteger(0)

            val client = KtorEregClient(
                baseUrl = "https://ereg-services.dev.intern.nav.no",
                httpClient = createHttpClient(
                    MockEngine { request ->
                        val current = activeRequests.incrementAndGet()
                        maxConcurrentRequests.updateAndGet { maxOf(it, current) }
                        delay(50)
                        activeRequests.decrementAndGet()

                        when (request.url.encodedPath.substringAfter("/v1/organisasjon/").substringBefore("/noekkelinfo")) {
                            "123456789" -> respondJson(
                                """
                                {
                                  "adresse": {
                                    "type": "Forretningsadresse",
                                    "adresselinje1": "Storgata 1",
                                    "postnummer": "0001",
                                    "landkode": "NO",
                                    "kommunenummer": "0301"
                                  }
                                }
                                """.trimIndent(),
                            )

                            "987654321" -> respond(
                                content = ByteReadChannel(""),
                                status = HttpStatusCode.NotFound,
                                headers = emptyJsonHeaders(),
                            )

                            else -> respond(
                                content = ByteReadChannel("""{"message":"uventet"}"""),
                                status = HttpStatusCode.BadGateway,
                                headers = emptyJsonHeaders(),
                            )
                        }
                    },
                ),
                gyldigDatoProvider = { "2026-06-24" },
            )

            val results = client.hentNoekkelinfo(listOf("123456789", "987654321", "555555555"))

            results shouldHaveSize 3
            results shouldBe listOf(
                EregResult.Funnet(
                    organisasjonsnummer = "123456789",
                    organisasjon = Organisasjon(
                        adresse = Adresse(
                            type = "Forretningsadresse",
                            adresselinje1 = "Storgata 1",
                            postnummer = "0001",
                            landkode = "NO",
                            kommunenummer = "0301",
                        ),
                    ),
                ),
                EregResult.IkkeFunnet("987654321"),
                EregResult.Feil(
                    organisasjonsnummer = "555555555",
                    melding = """Ereg svarte med status 502: {"message":"uventet"}""",
                ),
            )
            maxConcurrentRequests.get() shouldBeGreaterThan 1
        }
    })

private fun createHttpClient(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
    configureEregHttpClient()
}

private fun emptyJsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun MockRequestHandleScope.respondJson(json: String) = respond(
    content = ByteReadChannel(json),
    status = HttpStatusCode.OK,
    headers = emptyJsonHeaders(),
)
