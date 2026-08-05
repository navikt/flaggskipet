package no.nav.flaggskipet.infrastructure.clients.texas

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import java.net.URI

class TexasClientTest :
    FunSpec({
        val config = TexasConfig(introspectionEndpoint = URI("http://localhost:3000/api/v1/introspect"))

        test("introspectToken poster identity_provider og token som json og mapper aktivt svar") {
            val mockEngine = MockEngine { request ->
                request.method shouldBe HttpMethod.Post
                request.url.toString() shouldBe "http://localhost:3000/api/v1/introspect"
                request.body.contentType shouldBe ContentType.Application.Json
                with(request.body.toByteArray().decodeToString()) {
                    shouldContain(""""identity_provider":"tokenx"""")
                    shouldContain(""""token":"et-token"""")
                }

                respond(
                    content = ByteReadChannel(
                        """
                        {
                          "active": true,
                          "acr": "Level4",
                          "client_id": "dev-gcp:team-esyfo:syfo-oppfolgingsplan-frontend",
                          "pid": "ignorert-claim"
                        }
                        """.trimIndent(),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
            val client = TexasClient(httpClient = createTestHttpClient(mockEngine), config = config)

            client.introspectToken(IDENTITY_PROVIDER_TOKENX, "et-token") shouldBe TexasIntrospectionResponse(
                active = true,
                acr = "Level4",
                clientId = "dev-gcp:team-esyfo:syfo-oppfolgingsplan-frontend",
            )
        }

        test("introspectToken mapper inaktivt svar med feilmelding") {
            val client = TexasClient(
                httpClient = createTestHttpClient(
                    MockEngine {
                        respond(
                            content = ByteReadChannel("""{"active": false, "error": "token is expired"}"""),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                ),
                config = config,
            )

            client.introspectToken(IDENTITY_PROVIDER_TOKENX, "utløpt-token") shouldBe TexasIntrospectionResponse(
                active = false,
                error = "token is expired",
            )
        }
    })

private fun createTestHttpClient(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
    expectSuccess = true

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}
