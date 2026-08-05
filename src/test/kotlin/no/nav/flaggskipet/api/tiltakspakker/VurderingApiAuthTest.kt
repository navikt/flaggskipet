package no.nav.flaggskipet.api.tiltakspakker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.flaggskipet.api.auth.installAuthentication
import no.nav.flaggskipet.api.installPlugins
import no.nav.flaggskipet.api.internal.configureInternalApi
import no.nav.flaggskipet.application.VurderTiltakspakkerUseCase
import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.application.port.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.application.port.VurderingForLagring
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat
import no.nav.flaggskipet.infrastructure.HealthCheck
import no.nav.flaggskipet.infrastructure.HealthResult
import no.nav.flaggskipet.infrastructure.clients.texas.TexasClient
import no.nav.flaggskipet.infrastructure.clients.texas.TexasIntrospectionResponse

private const val VURDERING_PATH = "/api/v1/tiltakspakker/vurdering"

class VurderingApiAuthTest :
    FunSpec({
        test("gyldig token gir 200") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                val response = postVurdering(token = "gyldig-token")

                response.status shouldBe HttpStatusCode.OK
                with(response.bodyAsText()) {
                    shouldContain(""""orgnummer":"313644480"""")
                    shouldContain(""""deltakelse":"UTENFOR_SCOPE"""")
                }
            }
        }

        test("gyldig token med acr idporten-loa-high gir 200") {
            testApplication {
                setupApi(aktivtToken(acr = "idporten-loa-high"))

                postVurdering(token = "gyldig-token").status shouldBe HttpStatusCode.OK
            }
        }

        test("manglende token gir 401 med api error") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                val response = postVurdering(token = null)

                response.status shouldBe HttpStatusCode.Unauthorized
                with(response.bodyAsText()) {
                    shouldContain(""""status":401""")
                    shouldContain(""""type":"AUTHENTICATION_ERROR"""")
                    shouldContain(""""path":"$VURDERING_PATH"""")
                }
            }
        }

        test("inaktivt eller utløpt token gir 401") {
            testApplication {
                setupApi(
                    TexasIntrospectionResponse(active = false, error = "token is expired"),
                )

                val response = postVurdering(token = "utløpt-token")

                response.status shouldBe HttpStatusCode.Unauthorized
                response.bodyAsText() shouldContain """"type":"AUTHENTICATION_ERROR""""
            }
        }

        test("token med feil audience avvises av Texas og gir 401") {
            testApplication {
                setupApi(
                    TexasIntrospectionResponse(active = false, error = "invalid audience"),
                )

                postVurdering(token = "feil-audience-token").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("token med for lavt sikkerhetsnivå gir 403") {
            testApplication {
                setupApi(aktivtToken(acr = "idporten-loa-substantial"))

                val response = postVurdering(token = "lavt-nivå-token")

                response.status shouldBe HttpStatusCode.Forbidden
                response.bodyAsText() shouldContain """"type":"AUTHORIZATION_ERROR""""
            }
        }

        test("feilende introspeksjon gir 401") {
            testApplication {
                val texasClient = mockk<TexasClient>()
                coEvery { texasClient.introspectToken(any(), any()) } throws RuntimeException("texas er nede")
                setupApi(texasClient)

                postVurdering(token = "et-token").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("flere enn 20 orgnumre gir 400") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                val orgnumre = List(21) { """"3136444$it"""" }.joinToString(",")
                val response = client.post(VURDERING_PATH) {
                    header(HttpHeaders.Authorization, "Bearer gyldig-token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"orgnumre":[$orgnumre]}""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                with(response.bodyAsText()) {
                    shouldContain(""""type":"BAD_REQUEST"""")
                    shouldContain("Maks 20 orgnumre per kall")
                }
            }
        }

        test("20 orgnumre gir 200") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                val orgnumre = List(20) { """"3136444$it"""" }.joinToString(",")
                val response = client.post(VURDERING_PATH) {
                    header(HttpHeaders.Authorization, "Bearer gyldig-token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"orgnumre":[$orgnumre]}""")
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("internal-endepunkter er åpne uten token") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                client.get("/internal/health/is_alive").status shouldBe HttpStatusCode.OK
                client.get("/internal/health/is_ready").status shouldBe HttpStatusCode.OK
            }
        }
    })

private fun aktivtToken(acr: String) = TexasIntrospectionResponse(
    active = true,
    acr = acr,
    clientId = "dev-gcp:team-esyfo:syfo-oppfolgingsplan-frontend",
)

private fun ApplicationTestBuilder.setupApi(introspectionResponse: TexasIntrospectionResponse) {
    val texasClient = mockk<TexasClient>()
    coEvery { texasClient.introspectToken(any(), any()) } returns introspectionResponse
    setupApi(texasClient)
}

private fun ApplicationTestBuilder.setupApi(texasClient: TexasClient) {
    application {
        installPlugins()
        dependencies {
            provide<TexasClient> { texasClient }
            provide<VurderTiltakspakkerUseCase> {
                VurderTiltakspakkerUseCase(FakeEregClient(), FakeTiltakspakkeVurderingRepository())
            }
            provide<HealthCheck> { FakeHealthCheck() }
            provide { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
        }
        installAuthentication()
        configureVurderingApi()
        configureInternalApi()
    }
}

private suspend fun ApplicationTestBuilder.postVurdering(token: String?) = client.post(VURDERING_PATH) {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    contentType(ContentType.Application.Json)
    setBody("""{"orgnumre":["313644480"]}""")
}

private class FakeEregClient : EregClient {
    override suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregNoekkelinfo> = organisasjonsnummer.map { EregNoekkelinfo(organisasjonsnummer = it, adresse = null) }
}

private class FakeTiltakspakkeVurderingRepository : TiltakspakkeVurderingRepository {
    override suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<Vurderingsresultat> = emptyList()

    override suspend fun lagreVurderinger(vurderinger: Collection<VurderingForLagring>) = Unit
}

private class FakeHealthCheck : HealthCheck {
    override suspend fun check() = HealthResult(healthy = true, message = "ok")
}
