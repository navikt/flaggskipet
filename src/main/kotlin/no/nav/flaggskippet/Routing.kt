package no.nav.flaggskippet

import io.ktor.server.routing.routing
import no.nav.flaggskippet.api.registerMetricApi
import no.nav.flaggskippet.api.registerPodApi

fun io.ktor.server.application.Application.configureRouting(applicationState: ApplicationState) {
    routing {
        registerPodApi(applicationState)
        registerMetricApi()
    }
}
