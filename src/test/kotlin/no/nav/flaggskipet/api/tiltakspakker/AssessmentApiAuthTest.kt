package no.nav.flaggskipet.api.tiltakspakker

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.flaggskipet.api.auth.TOKENX_AUTHENTICATION
import no.nav.flaggskipet.api.auth.TokenXPrincipal
import no.nav.flaggskipet.api.auth.installAuthentication
import no.nav.flaggskipet.api.auth.introspectTokenForAuthentication
import no.nav.flaggskipet.api.installPlugins
import no.nav.flaggskipet.api.internal.configureInternalApi
import no.nav.flaggskipet.application.AssessTiltakspakkerUseCase
import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.application.port.NewAssessment
import no.nav.flaggskipet.application.port.TiltakspakkeAssessmentRepository
import no.nav.flaggskipet.domain.assessment.AssessmentResult
import no.nav.flaggskipet.infrastructure.HealthCheck
import no.nav.flaggskipet.infrastructure.HealthResult
import no.nav.flaggskipet.infrastructure.clients.texas.TexasClient
import no.nav.flaggskipet.infrastructure.clients.texas.TexasIntrospectionResponse
import org.slf4j.LoggerFactory

private const val ASSESSMENT_PATH = "/api/v1/tiltakspakker/vurdering"

class AssessmentApiAuthTest :
    FunSpec({
        listOf(
            emptyList<String>() to "EMPTY_ORGNUMRE",
            uniqueOrgnumre(101) to "TOO_MANY_ORGNUMRE",
            listOf("invalid-orgnummer-canary") to "INVALID_ORGNUMMER_FORMAT",
        ).forEach { (orgnumre, reason) ->
            test("rejection log distinguishes $reason without changing the API response") {
                withApplicationLog { logEvents ->
                    testApplication {
                        setupApi(activeToken(acr = "Level4"))
                        val response = postAssessmentWithOrgnumre(orgnumre)
                        response.status shouldBe HttpStatusCode.BadRequest
                        response.bodyAsText() shouldContain "\"type\":\"BAD_REQUEST\""
                    }

                    val logs = logEvents.list.filter { it.level.isGreaterOrEqual(Level.WARN) }
                    logs.size shouldBe 1
                    logs.single().level shouldBe Level.WARN
                    val json = logs.single().serializedJson()
                    json.value("rejection_reason") shouldBe reason
                    json.toString() shouldNotContain "invalid-orgnummer-canary"
                    json.toString() shouldNotContain "gyldig-token"
                }
            }
        }

        listOf(
            Triple(null, activeToken("Level4"), "MISSING_BEARER_TOKEN"),
            Triple("token-canary", TexasIntrospectionResponse(active = false), "INACTIVE_TOKEN"),
            Triple("token-canary", activeToken("idporten-loa-substantial"), "INSUFFICIENT_SECURITY_LEVEL"),
        ).forEach { (token, introspection, reason) ->
            test("rejection log distinguishes $reason without token details") {
                withApplicationLog { logEvents ->
                    testApplication {
                        setupApi(introspection)
                        val expectedStatus = if (reason == "INSUFFICIENT_SECURITY_LEVEL") HttpStatusCode.Forbidden else HttpStatusCode.Unauthorized
                        postAssessment(token).status shouldBe expectedStatus
                    }

                    val logs = logEvents.list.filter { it.level.isGreaterOrEqual(Level.WARN) }
                    logs.size shouldBe 1
                    logs.single().level shouldBe Level.WARN
                    val json = logs.single().serializedJson()
                    json.value("rejection_reason") shouldBe reason
                    json.toString() shouldNotContain "token-canary"
                }
            }
        }

        test("valid token returns 200") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                val response = postAssessment(token = "gyldig-token")

                response.status shouldBe HttpStatusCode.OK
                with(response.bodyAsText()) {
                    shouldContain(""""orgnummer":"313644480"""")
                    shouldContain(""""deltakelse":"UTENFOR_SCOPE"""")
                }
            }
        }

        test("valid token with acr idporten-loa-high returns 200") {
            testApplication {
                setupApi(activeToken(acr = "idporten-loa-high"))

                postAssessment(token = "gyldig-token").status shouldBe HttpStatusCode.OK
            }
        }

        test("missing token returns 401 with api error") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                val response = postAssessment(token = null)

                response.status shouldBe HttpStatusCode.Unauthorized
                with(response.bodyAsText()) {
                    shouldContain(""""status":401""")
                    shouldContain(""""type":"AUTHENTICATION_ERROR"""")
                    shouldContain(""""path":"$ASSESSMENT_PATH"""")
                }
            }
        }

        test("inactive or expired token returns 401") {
            withApplicationLog { logEvents ->
                testApplication {
                    setupApi(
                        TexasIntrospectionResponse(
                            active = false,
                            error = "token is expired for 12345678901 at https://texas.example.test/token",
                        ),
                    )

                    val response = postAssessment(token = "incoming-access-token-canary")

                    response.status shouldBe HttpStatusCode.Unauthorized
                    response.bodyAsText() shouldContain """"type":"AUTHENTICATION_ERROR""""
                }

                val warningsAndErrors = logEvents.list.filter { it.level.isGreaterOrEqual(Level.WARN) }
                warningsAndErrors.size shouldBe 1
                warningsAndErrors.single().level shouldBe Level.WARN

                val serialized = warningsAndErrors.single().serializedJson()
                serialized.value("event_type") shouldBe "api_request_rejected"
                serialized.value("error_code") shouldBe "AUTHENTICATION_ERROR"
                serialized.value("operation") shouldBe "vurder_tiltakspakker"
                with(serialized.toString()) {
                    shouldNotContain("12345678901")
                    shouldNotContain("https://texas.example.test/token")
                    shouldNotContain("incoming-access-token-canary")
                    shouldNotContain("token is expired")
                }
            }
        }

        test("Texas rejects token with wrong audience with 401") {
            testApplication {
                setupApi(
                    TexasIntrospectionResponse(active = false, error = "invalid audience"),
                )

                postAssessment(token = "feil-audience-token").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("token with insufficient assurance level returns 403") {
            testApplication {
                setupApi(activeToken(acr = "idporten-loa-substantial"))

                val response = postAssessment(token = "lavt-nivå-token")

                response.status shouldBe HttpStatusCode.Forbidden
                response.bodyAsText() shouldContain """"type":"AUTHORIZATION_ERROR""""
            }
        }

        test("technical introspection failure returns one safe 500 error") {
            withApplicationLog { logEvents ->
                testApplication {
                    val texasClient = mockk<TexasClient>()
                    coEvery { texasClient.introspectToken(any(), any()) } throws RuntimeException(
                        "Texas response body for 12345678901 from https://texas.example.test/token " +
                            "with access-token-canary",
                    )
                    setupApi(texasClient)

                    val response = postAssessment(token = "incoming-access-token-canary")

                    response.status shouldBe HttpStatusCode.InternalServerError
                    with(response.bodyAsText()) {
                        shouldContain(""""type":"TEXAS_INTROSPECTION_FAILED"""")
                        shouldContain("Authentication service unavailable")
                        shouldNotContain("12345678901")
                        shouldNotContain("https://texas.example.test/token")
                        shouldNotContain("access-token-canary")
                    }
                }

                val warningsAndErrors = logEvents.list.filter { it.level.isGreaterOrEqual(Level.WARN) }
                warningsAndErrors.size shouldBe 1
                warningsAndErrors.single().level shouldBe Level.ERROR

                val serialized = warningsAndErrors.single().serializedJson()
                serialized.value("event_type") shouldBe "api_request_failed"
                serialized.value("error_code") shouldBe "TEXAS_INTROSPECTION_FAILED"
                serialized.value("operation") shouldBe "vurder_tiltakspakker"
                serialized.value("exception_type") shouldBe "ApiErrorException"
                with(serialized.toString()) {
                    shouldNotContain("12345678901")
                    shouldNotContain("https://texas.example.test/token")
                    shouldNotContain("incoming-access-token-canary")
                    shouldNotContain("access-token-canary")
                    shouldNotContain("Texas response body")
                }
            }
        }

        test("cancellation from Texas propagates unchanged") {
            val cancellation = CancellationException("cancellation-canary")
            val texasClient = mockk<TexasClient>()
            coEvery { texasClient.introspectToken(any(), any()) } throws cancellation

            val thrown = runCatching {
                introspectTokenForAuthentication(texasClient, "incoming-access-token-canary")
            }.exceptionOrNull()

            thrown shouldBe cancellation
        }

        test("more than 100 unique orgnumre returns 400") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                val response = postAssessmentWithOrgnumre(uniqueOrgnumre(101))

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("100 unique orgnumre returns 200") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                postAssessmentWithOrgnumre(uniqueOrgnumre(100)).status shouldBe HttpStatusCode.OK
            }
        }

        test("duplicate orgnumre count once") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                postAssessmentWithOrgnumre(List(25) { "313644480" }).status shouldBe HttpStatusCode.OK
            }
        }

        test("empty list returns 400") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                val response = postAssessmentWithOrgnumre(emptyList())

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("orgnummer without exactly 9 digits returns 400") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                listOf("12345678", "1234567890", "31364448a", "313644480/../admin", "").forEach { invalid ->
                    val response = postAssessmentWithOrgnumre(listOf("313644480", invalid))

                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        test("principal with client_id is available after successful authentication") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                val response = client.get("/principal-test") {
                    header(HttpHeaders.Authorization, "Bearer gyldig-token")
                }

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "dev-gcp:team-esyfo:syfo-oppfolgingsplan-frontend"
            }
        }

        test("internal endpoints remain open without a token") {
            testApplication {
                setupApi(activeToken(acr = "Level4"))

                client.get("/internal/health/is_alive").status shouldBe HttpStatusCode.OK
                client.get("/internal/health/is_ready").status shouldBe HttpStatusCode.OK
            }
        }
    })

private fun activeToken(acr: String) = TexasIntrospectionResponse(
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
            provide<AssessTiltakspakkerUseCase> {
                AssessTiltakspakkerUseCase(FakeEregClient(), FakeTiltakspakkeAssessmentRepository())
            }
            provide<HealthCheck> { FakeHealthCheck() }
            provide { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
        }
        installAuthentication()
        configureAssessmentApi()
        configureInternalApi()
        routing {
            authenticate(TOKENX_AUTHENTICATION) {
                get("/principal-test") {
                    call.respondText(call.principal<TokenXPrincipal>()?.clientId ?: "no principal")
                }
            }
        }
    }
}

private suspend fun ApplicationTestBuilder.postAssessment(token: String?) = client.post(ASSESSMENT_PATH) {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    contentType(ContentType.Application.Json)
    setBody("""{"orgnumre":["313644480"]}""")
}

private suspend fun ApplicationTestBuilder.postAssessmentWithOrgnumre(orgnumre: List<String>) = client.post(ASSESSMENT_PATH) {
    header(HttpHeaders.Authorization, "Bearer gyldig-token")
    contentType(ContentType.Application.Json)
    setBody("""{"orgnumre":[${orgnumre.joinToString(",") { "\"$it\"" }}]}""")
}

private fun uniqueOrgnumre(count: Int): List<String> = List(count) { "31364%04d".format(it) }

private class FakeEregClient : EregClient {
    override suspend fun getNoekkelinfo(orgnummer: List<String>): List<EregNoekkelinfo> = orgnummer.map { EregNoekkelinfo(orgnummer = it, adresse = null) }
}

private class FakeTiltakspakkeAssessmentRepository : TiltakspakkeAssessmentRepository {
    override suspend fun getAssessments(
        orgnumre: Collection<String>,
        tiltakspakkeIds: Collection<String>,
    ): List<AssessmentResult> = emptyList()

    override suspend fun saveAssessments(
        assessments: Collection<NewAssessment>,
    ): List<AssessmentResult> = assessments.map { assessment ->
        AssessmentResult(
            tiltakspakkeId = assessment.tiltakspakkeId,
            orgnummer = assessment.orgnummer,
            participation = assessment.participation,
            fylkeskode = assessment.fylkeskode,
            assessmentReason = assessment.assessmentReason,
        )
    }
}

private class FakeHealthCheck : HealthCheck {
    override suspend fun check() = HealthResult(healthy = true, message = "ok")
}

private suspend fun withApplicationLog(block: suspend (ListAppender<ILoggingEvent>) -> Unit) {
    val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    val logEvents = ListAppender<ILoggingEvent>().apply {
        start()
        logger.addAppender(this)
    }
    try {
        block(logEvents)
    } finally {
        logger.detachAppender(logEvents)
        logEvents.stop()
    }
}

private fun ILoggingEvent.serializedJson(): JsonObject {
    val encoder = LogstashEncoder().apply {
        context = LoggerFactory.getILoggerFactory() as LoggerContext
        start()
    }
    return try {
        Json.parseToJsonElement(encoder.encode(this).decodeToString()).jsonObject
    } finally {
        encoder.stop()
    }
}

private fun JsonObject.value(field: String): String = getValue(field).jsonPrimitive.content
