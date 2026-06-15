package no.nav.flaggskipet

import io.ktor.server.routing.routing
import no.nav.flaggskipet.api.registerMetricApi
import no.nav.flaggskipet.api.registerPodApi

fun io.ktor.server.application.Application.configureRouting(applicationState: ApplicationState) {
    routing {
        registerPodApi(applicationState)
        registerMetricApi()
    }
}
