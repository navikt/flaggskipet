package no.nav.flaggskipet.api.tiltakspakker

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.flaggskipet.api.auth.TOKENX_AUTHENTICATION
import no.nav.flaggskipet.api.auth.TokenXPrincipal
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
import org.slf4j.LoggerFactory

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

        test("flere enn 100 unike orgnumre gir 400") {
            medVurderingApiLogg { loggmeldinger ->
                testApplication {
                    setupApi(aktivtToken(acr = "Level4"))

                    val response = postVurderingMedOrgnumre(unikeOrgnumre(101))

                    response.status shouldBe HttpStatusCode.BadRequest
                    with(response.bodyAsText()) {
                        shouldContain(""""type":"BAD_REQUEST"""")
                        shouldContain("Maks 100 unike orgnumre per kall")
                    }
                }
                with(loggmeldinger.list.single()) {
                    level shouldBe Level.WARN
                    formattedMessage shouldBe "Avviser vurderingskall med 101 unike orgnumre; maks er 100"
                }
            }
        }

        test("100 unike orgnumre gir 200") {
            medVurderingApiLogg { loggmeldinger ->
                testApplication {
                    setupApi(aktivtToken(acr = "Level4"))

                    postVurderingMedOrgnumre(unikeOrgnumre(100)).status shouldBe HttpStatusCode.OK
                }
                loggmeldinger.list.isEmpty() shouldBe true
            }
        }

        test("duplikater telles som ett orgnummer") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                postVurderingMedOrgnumre(List(25) { "313644480" }).status shouldBe HttpStatusCode.OK
            }
        }

        test("tom liste gir 400") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                val response = postVurderingMedOrgnumre(emptyList())

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "orgnumre kan ikke være tom"
            }
        }

        test("orgnummer som ikke er 9 sifre gir 400") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                listOf("12345678", "1234567890", "31364448a", "313644480/../admin", "").forEach { ugyldig ->
                    val response = postVurderingMedOrgnumre(listOf("313644480", ugyldig))

                    response.status shouldBe HttpStatusCode.BadRequest
                    response.bodyAsText() shouldContain "hvert orgnummer må være nøyaktig 9 sifre"
                }
            }
        }

        test("principal med client_id er tilgjengelig etter vellykket autentisering") {
            testApplication {
                setupApi(aktivtToken(acr = "Level4"))

                val response = client.get("/principal-test") {
                    header(HttpHeaders.Authorization, "Bearer gyldig-token")
                }

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "dev-gcp:team-esyfo:syfo-oppfolgingsplan-frontend"
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
        routing {
            authenticate(TOKENX_AUTHENTICATION) {
                get("/principal-test") {
                    call.respondText(call.principal<TokenXPrincipal>()?.clientId ?: "ingen principal")
                }
            }
        }
    }
}

private suspend fun ApplicationTestBuilder.postVurdering(token: String?) = client.post(VURDERING_PATH) {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    contentType(ContentType.Application.Json)
    setBody("""{"orgnumre":["313644480"]}""")
}

private suspend fun ApplicationTestBuilder.postVurderingMedOrgnumre(orgnumre: List<String>) = client.post(VURDERING_PATH) {
    header(HttpHeaders.Authorization, "Bearer gyldig-token")
    contentType(ContentType.Application.Json)
    setBody("""{"orgnumre":[${orgnumre.joinToString(",") { "\"$it\"" }}]}""")
}

private fun unikeOrgnumre(antall: Int): List<String> = List(antall) { "31364%04d".format(it) }

private suspend fun medVurderingApiLogg(block: suspend (ListAppender<ILoggingEvent>) -> Unit) {
    val logger = LoggerFactory.getLogger("no.nav.flaggskipet.api.tiltakspakker.VurderingApiKt") as Logger
    val loggmeldinger = ListAppender<ILoggingEvent>().apply {
        start()
        logger.addAppender(this)
    }
    try {
        block(loggmeldinger)
    } finally {
        logger.detachAppender(loggmeldinger)
        loggmeldinger.stop()
    }
}

private class FakeEregClient : EregClient {
    override suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregNoekkelinfo> = organisasjonsnummer.map { EregNoekkelinfo(organisasjonsnummer = it, adresse = null) }
}

private class FakeTiltakspakkeVurderingRepository : TiltakspakkeVurderingRepository {
    override suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<Vurderingsresultat> = emptyList()

    override suspend fun lagreVurderinger(
        vurderinger: Collection<VurderingForLagring>,
    ): List<Vurderingsresultat> = vurderinger.map { vurdering ->
        Vurderingsresultat(
            tiltakspakkeId = vurdering.tiltakspakkeId,
            orgnummer = vurdering.orgnummer,
            deltakelse = vurdering.deltakelse,
            fylkeskode = vurdering.fylkeskode,
            vurderingsgrunn = vurdering.vurderingsgrunn,
        )
    }
}

private class FakeHealthCheck : HealthCheck {
    override suspend fun check() = HealthResult(healthy = true, message = "ok")
}
