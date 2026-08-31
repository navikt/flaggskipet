package no.nav.flaggskipet.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.api.error.determineApiError
import no.nav.flaggskipet.api.error.logApiError
import java.util.UUID

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"

fun Application.installPlugins() {
    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }

    install(StatusPages) {
        exception<TimeoutCancellationException> { call, cause ->
            call.respondApiError(cause)
        }
        exception<CancellationException> { call, _ ->
            // Ktor logger ellers exception-meldingen som rå ERROR før fallback-500.
            withContext(NonCancellable) {
                call.respond(HttpStatusCode(499, "Client Closed Request"))
            }
        }
        exception<Throwable> { call, cause ->
            call.respondApiError(cause)
        }
    }
}

private suspend fun ApplicationCall.respondApiError(cause: Throwable) {
    val apiError = determineApiError(cause, request.path())
    logApiError(apiError, cause)
    respond(HttpStatusCode.fromValue(apiError.status), apiError)
}
