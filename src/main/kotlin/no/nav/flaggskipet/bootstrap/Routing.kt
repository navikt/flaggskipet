package no.nav.flaggskipet.bootstrap

import io.ktor.server.routing.routing
import no.nav.flaggskipet.api.error.installStatusPages
import no.nav.flaggskipet.api.internal.registerMetricApi
import no.nav.flaggskipet.api.internal.registerPodApi
import no.nav.flaggskipet.api.plugins.installCallId
import no.nav.flaggskipet.api.plugins.installContentNegotiation

fun io.ktor.server.application.Application.configureRouting(applicationState: ApplicationState) {
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(applicationState)
        registerMetricApi()
    }
}
