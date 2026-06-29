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
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.domain.vurdering.Adresse
import org.junit.jupiter.api.assertThrows

class HttpClientImplTest :
    FunSpec({
        test("hentNoekkelinfo mapper 200 til EregNoekkelinfo med adresse og sender gyldigDato for i dag") {
            val mockEngine = MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.protocol shouldBe URLProtocol.HTTPS
                request.url.host shouldBe "ereg-services.dev.intern.nav.no"
                request.url.encodedPath shouldBe "/v1/organisasjon/313644480/noekkelinfo"
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

            client.hentNoekkelinfo(listOf("313644480")) shouldBe listOf(
                EregNoekkelinfo(
                    organisasjonsnummer = "313644480",
                    adresse = Adresse(
                        type = "Forretningsadresse",
                        postnummer = "9012",
                        kommunenummer = "5401",
                    ),
                ),
            )
        }

        test("hentNoekkelinfo mapper 404 til EregNoekkelinfo med null adresse") {
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

            client.hentNoekkelinfo(listOf("999999999")) shouldBe listOf(
                EregNoekkelinfo(organisasjonsnummer = "999999999", adresse = null),
            )
        }

        test("hentNoekkelinfo kaster feil, når ereg feiler") {
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

            assertThrows<RuntimeException> { client.hentNoekkelinfo(listOf("111111111")) }
        }
    })

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
