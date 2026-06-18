package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.flaggskipet.api.error.installStatusPages
import no.nav.flaggskipet.api.internal.registerMetricApi
import no.nav.flaggskipet.api.internal.registerPodApi
import no.nav.flaggskipet.api.plugins.installCallId
import no.nav.flaggskipet.api.plugins.installContentNegotiation
import no.nav.flaggskipet.infrastructure.db.DatabaseHealthIndicator

fun Application.configureRouting(
    applicationState: ApplicationState,
    databaseHealthIndicator: DatabaseHealthIndicator,
) {
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(applicationState, databaseHealthIndicator)
        registerMetricApi()
    }
}
