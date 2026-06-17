package no.nav.flaggskipet.api.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.logException(cause)
            val apiError = determineApiError(cause, call.request.path())
            call.respond(HttpStatusCode.fromValue(apiError.status), apiError)
        }
    }
}
