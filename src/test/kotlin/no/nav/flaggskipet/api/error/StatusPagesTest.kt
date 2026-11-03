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
        listOf("/illegal-argument", "/wrapped-technical-error").forEach { path ->
            test("technical failure at $path remains ERROR even with a 400 response") {
                withApplicationLog { logEvents ->
                    testApplication {
                        application { installPluginTestRoutes() }
                        client.get(path).status shouldBe HttpStatusCode.BadRequest
                    }

                    val logger = logEvents.list.filter { it.level.isGreaterOrEqual(Level.WARN) }
                    logger.size shouldBe 1
                    logger.single().level shouldBe Level.ERROR
                    val serialized = logger.single().serializedJson()
                    serialized.value("event_type") shouldBe "api_request_failed"
                    serialized.toString() shouldNotContain "12345678901"
                    serialized.toString() shouldNotContain "technical-token-canary"
                }
            }
        }

        test("status pages respond with api error for not found exception") {
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

        test("expected client rejection logs one safe warning and no error") {
            withApplicationLog { logEvents ->
                testApplication {
                    application {
                        installPluginTestRoutes()
                    }

                    client.get("/api/v1/tiltakspakker/vurdering").status shouldBe HttpStatusCode.BadRequest
                }

                val warningsAndErrors = logEvents.list.filter { it.level.isGreaterOrEqual(Level.WARN) }
                warningsAndErrors.size shouldBe 1
                warningsAndErrors.single().level shouldBe Level.WARN

                val serialized = warningsAndErrors.single().serializedJson()
                serialized.value("event_type") shouldBe "api_request_rejected"
                serialized.value("error_code") shouldBe "BAD_REQUEST"
                serialized.value("operation") shouldBe "vurder_tiltakspakker"
                serialized.value("message") shouldContain "Rejecting API request"

                serialized.toString() shouldNotContain "12345678901"
                serialized.toString() shouldNotContain "https://example.test/person/123"
                serialized.containsKey("stack_trace") shouldBe false
            }
        }

        test("production encoder serializes a valid trace id") {
            withApplicationLog { logEvents ->
                val traceId = "0123456789abcdef0123456789abcdef"
                MDC.put("trace_id", traceId)
                try {
                    LoggerFactory.getLogger("Application").logApiError(
                        apiError = ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            type = ErrorType.BAD_REQUEST,
                            message = "Bad request",
                        ),
                        cause = ApiErrorException.BadRequest("Sensitive cause 12345678901"),
                        operation = ApiOperation.ASSESS_TILTAKSPAKKER,
                    )

                    val serialized = logEvents.list.single().serializedJson()
                    serialized.value("trace_id") shouldBe traceId
                    serialized.value("trace_id").matches(Regex("^[0-9a-f]{32}$")) shouldBe true
                    serialized.toString() shouldNotContain "12345678901"
                } finally {
                    MDC.remove("trace_id")
                }
            }
        }

        test("unexpected server failure logs one structured error") {
            withApplicationLog { logEvents ->
                testApplication {
                    application {
                        installPluginTestRoutes()
                    }

                    client.get("/internal-error").status shouldBe HttpStatusCode.InternalServerError
                }

                val errorLogs = logEvents.list.filter { it.level == Level.ERROR }
                errorLogs.size shouldBe 1

                val serialized = errorLogs.single().serializedJson()
                serialized.value("event_type") shouldBe "api_request_failed"
                serialized.value("error_code") shouldBe "INTERNAL_SERVER_ERROR"
                serialized.value("operation") shouldBe "behandle_api_kall"
                serialized.value("exception_type") shouldBe "IllegalStateException"
                serialized.toString() shouldNotContain "12345678901"
                serialized.toString() shouldNotContain "https://example.test/person/123"
            }
        }

        test("server failure retains safe cause type and code location without messages") {
            withApplicationLog { logEvents ->
                LoggerFactory.getLogger("Application").logApiError(
                    apiError = ApiError(
                        status = HttpStatusCode.InternalServerError.value,
                        type = ErrorType.TEXAS_INTROSPECTION_FAILED,
                        message = "Authentication service unavailable",
                    ),
                    cause = ApiErrorException.InternalServerError(
                        errorMessage = "External failure for 12345678901 with incoming-access-token-canary",
                        cause = sensitiveRootCause(),
                        type = ErrorType.TEXAS_INTROSPECTION_FAILED,
                    ),
                    operation = ApiOperation.ASSESS_TILTAKSPAKKER,
                )

                val serialized = logEvents.list.single().serializedJson()
                val stackTrace = serialized.value("stack_trace")

                serialized.value("exception_type") shouldBe "ApiErrorException"
                stackTrace shouldContain "RuntimeException: ApiErrorException"
                stackTrace shouldContain "Caused by: java.lang.RuntimeException: IllegalArgumentException"
                stackTrace shouldContain "sensitiveRootCause"
                with(serialized.toString()) {
                    shouldNotContain("12345678901")
                    shouldNotContain("incoming-access-token-canary")
                    shouldNotContain("https://texas.example.test/token")
                    shouldNotContain("Sensitive root cause")
                    shouldNotContain("External failure")
                }
            }
        }

        test("internal timeout remains one structured 500 error") {
            withApplicationLog { logEvents ->
                testApplication {
                    application {
                        installPluginTestRoutes()
                    }

                    client.get("/internal-timeout").status shouldBe HttpStatusCode.InternalServerError
                }

                val errorLogs = logEvents.list.filter { it.level == Level.ERROR }
                errorLogs.size shouldBe 1

                val serialized = errorLogs.single().serializedJson()
                serialized.value("event_type") shouldBe "api_request_failed"
                serialized.value("error_code") shouldBe "INTERNAL_SERVER_ERROR"
                serialized.value("operation") shouldBe "behandle_api_kall"
                serialized.value("exception_type") shouldBe "TimeoutCancellationException"
            }
        }
    })

private fun Application.installPluginTestRoutes() {
    installPlugins()

    routing {
        get("/illegal-argument") {
            throw IllegalArgumentException("Unexpected internal value 12345678901 technical-token-canary")
        }
        get("/wrapped-technical-error") {
            throw ApiErrorException.BadRequest(
                "Invalid value",
                cause = IllegalStateException("Technical failure 12345678901 technical-token-canary"),
            )
        }
        get("/not-found") {
            throw NotFoundException("missing resource")
        }
        get("/api/v1/tiltakspakker/vurdering") {
            throw ApiErrorException.BadRequest(
                "Invalid value 12345678901 from https://example.test/person/123",
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

private fun sensitiveRootCause() = IllegalArgumentException(
    "Sensitive root cause 12345678901 from https://texas.example.test/token with incoming-access-token-canary",
)
