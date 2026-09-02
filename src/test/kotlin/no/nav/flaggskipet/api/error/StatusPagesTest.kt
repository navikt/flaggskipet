package no.nav.flaggskipet.api.error

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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.flaggskipet.api.installPlugins
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class StatusPagesTest :
    FunSpec({
        test("status pages svarer med api error for not found exception") {
            testApplication {
                application {
                    installPluginTestRoutes()
                }

                val response = client.get("/not-found")

                response.status shouldBe HttpStatusCode.NotFound
                response.headers["Content-Type"] shouldBe "application/json"
                with(response.bodyAsText()) {
                    shouldContain(""""status":404""")
                    shouldContain(""""type":"NOT_FOUND"""")
                    shouldContain(""""message":"missing resource"""")
                    shouldContain(""""path":"/not-found"""")
                    shouldContain(""""timestamp":"""")
                }
            }
        }

        test("forventet klientavvisning logger én trygg warn og ingen error") {
            medApplicationLogg { loggmeldinger ->
                testApplication {
                    application {
                        installPluginTestRoutes()
                    }

                    client.get("/api/v1/tiltakspakker/vurdering").status shouldBe HttpStatusCode.BadRequest
                }

                val warnOgError = loggmeldinger.list.filter { it.level.isGreaterOrEqual(Level.WARN) }
                warnOgError.size shouldBe 1
                warnOgError.single().level shouldBe Level.WARN

                val serialisert = warnOgError.single().serialisertJson()
                serialisert.verdi("event_type") shouldBe "api_request_rejected"
                serialisert.verdi("error_code") shouldBe "BAD_REQUEST"
                serialisert.verdi("operation") shouldBe "vurder_tiltakspakker"
                serialisert.verdi("message") shouldContain "Avviser API-kall"

                serialisert.toString() shouldNotContain "12345678901"
                serialisert.toString() shouldNotContain "https://example.test/person/123"
                serialisert.containsKey("stack_trace") shouldBe false
            }
        }

        test("produksjonsenkoderen serialiserer en gyldig trace id") {
            medApplicationLogg { loggmeldinger ->
                val traceId = "0123456789abcdef0123456789abcdef"
                MDC.put("trace_id", traceId)
                try {
                    LoggerFactory.getLogger("Application").logApiError(
                        apiError = ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            type = ErrorType.BAD_REQUEST,
                            message = "Bad request",
                        ),
                        cause = ApiErrorException.BadRequest("Sensitiv årsak 12345678901"),
                        operation = ApiOperation.VURDER_TILTAKSPAKKER,
                    )

                    val serialisert = loggmeldinger.list.single().serialisertJson()
                    serialisert.verdi("trace_id") shouldBe traceId
                    serialisert.verdi("trace_id").matches(Regex("^[0-9a-f]{32}$")) shouldBe true
                    serialisert.toString() shouldNotContain "12345678901"
                } finally {
                    MDC.remove("trace_id")
                }
            }
        }

        test("uventet serverfeil logger én strukturert error") {
            medApplicationLogg { loggmeldinger ->
                testApplication {
                    application {
                        installPluginTestRoutes()
                    }

                    client.get("/internal-error").status shouldBe HttpStatusCode.InternalServerError
                }

                val errorlogger = loggmeldinger.list.filter { it.level == Level.ERROR }
                errorlogger.size shouldBe 1

                val serialisert = errorlogger.single().serialisertJson()
                serialisert.verdi("event_type") shouldBe "api_request_failed"
                serialisert.verdi("error_code") shouldBe "INTERNAL_SERVER_ERROR"
                serialisert.verdi("operation") shouldBe "behandle_api_kall"
                serialisert.verdi("exception_type") shouldBe "IllegalStateException"
                serialisert.toString() shouldNotContain "12345678901"
                serialisert.toString() shouldNotContain "https://example.test/person/123"
            }
        }

        test("serverfeil beholder trygg årsakstype og kodeplassering uten meldinger") {
            medApplicationLogg { loggmeldinger ->
                LoggerFactory.getLogger("Application").logApiError(
                    apiError = ApiError(
                        status = HttpStatusCode.InternalServerError.value,
                        type = ErrorType.TEXAS_INTROSPECTION_FAILED,
                        message = "Authentication service unavailable",
                    ),
                    cause = ApiErrorException.InternalServerError(
                        errorMessage = "Ytre feil for 12345678901 med incoming-access-token-canary",
                        cause = sensitivRotårsak(),
                        type = ErrorType.TEXAS_INTROSPECTION_FAILED,
                    ),
                    operation = ApiOperation.VURDER_TILTAKSPAKKER,
                )

                val serialisert = loggmeldinger.list.single().serialisertJson()
                val stackTrace = serialisert.verdi("stack_trace")

                serialisert.verdi("exception_type") shouldBe "ApiErrorException"
                stackTrace shouldContain "RuntimeException: ApiErrorException"
                stackTrace shouldContain "Caused by: java.lang.RuntimeException: IllegalArgumentException"
                stackTrace shouldContain "sensitivRotårsak"
                with(serialisert.toString()) {
                    shouldNotContain("12345678901")
                    shouldNotContain("incoming-access-token-canary")
                    shouldNotContain("https://texas.example.test/token")
                    shouldNotContain("Sensitiv rotårsak")
                    shouldNotContain("Ytre feil")
                }
            }
        }

        test("intern timeout forblir én strukturert 500-feil") {
            medApplicationLogg { loggmeldinger ->
                testApplication {
                    application {
                        installPluginTestRoutes()
                    }

                    client.get("/internal-timeout").status shouldBe HttpStatusCode.InternalServerError
                }

                val errorlogger = loggmeldinger.list.filter { it.level == Level.ERROR }
                errorlogger.size shouldBe 1

                val serialisert = errorlogger.single().serialisertJson()
                serialisert.verdi("event_type") shouldBe "api_request_failed"
                serialisert.verdi("error_code") shouldBe "INTERNAL_SERVER_ERROR"
                serialisert.verdi("operation") shouldBe "behandle_api_kall"
                serialisert.verdi("exception_type") shouldBe "TimeoutCancellationException"
            }
        }
    })

private fun Application.installPluginTestRoutes() {
    installPlugins()

    routing {
        get("/not-found") {
            throw NotFoundException("missing resource")
        }
        get("/api/v1/tiltakspakker/vurdering") {
            throw ApiErrorException.BadRequest(
                "Ugyldig verdi 12345678901 fra https://example.test/person/123",
            )
        }
        get("/internal-error") {
            throw IllegalStateException(
                "Feil for 12345678901 ved https://example.test/person/123",
            )
        }
        get("/internal-timeout") {
            withTimeout(1) {
                delay(Long.MAX_VALUE)
            }
        }
        get("/ok") {
            call.respondText("ok")
        }
    }
}

private suspend fun medApplicationLogg(block: suspend (ListAppender<ILoggingEvent>) -> Unit) {
    val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
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

private fun ILoggingEvent.serialisertJson(): JsonObject {
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

private fun JsonObject.verdi(felt: String): String = getValue(felt).jsonPrimitive.content

private fun sensitivRotårsak() = IllegalArgumentException(
    "Sensitiv rotårsak 12345678901 fra https://texas.example.test/token med incoming-access-token-canary",
)
