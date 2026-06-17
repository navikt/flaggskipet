package no.nav.flaggskipet

import io.ktor.server.routing.routing
import no.nav.flaggskipet.api.registerMetricApi
import no.nav.flaggskipet.api.registerPodApi
import no.nav.flaggskipet.plugins.installCallId
import no.nav.flaggskipet.plugins.installContentNegotiation
import no.nav.flaggskipet.plugins.installStatusPages

fun io.ktor.server.application.Application.configureRouting(applicationState: ApplicationState) {
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(applicationState)
        registerMetricApi()
    }
}
