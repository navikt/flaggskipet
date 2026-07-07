package no.nav.flaggskipet.api.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.flaggskipet.bootstrap.ApplicationState
import no.nav.flaggskipet.infrastructure.database.config.isHealthy
import javax.sql.DataSource

private const val POD_HEALTH_PATH = "/internal/health"
const val POD_METRICS_PATH = "/internal/metrics"

fun Application.configureInternalApi(state: ApplicationState) {
    val dataSource: DataSource by dependencies
    val meterRegistry: PrometheusMeterRegistry by dependencies

    routing {
        registerPodApi(state, dataSource)
        registerMetricApi(meterRegistry)
    }
}

fun Routing.registerPodApi(
    applicationState: ApplicationState,
    dataSource: DataSource,
) {
    get("$POD_HEALTH_PATH/is_alive") {
        if (applicationState.alive) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get("$POD_HEALTH_PATH/is_ready") {
        if (isReady(applicationState, dataSource)) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun isReady(
    applicationState: ApplicationState,
    dataSource: DataSource,
) = applicationState.ready && dataSource.isHealthy()

fun Routing.registerMetricApi(meterRegistry: PrometheusMeterRegistry) {
    get(POD_METRICS_PATH) {
        call.respondText(meterRegistry.scrape())
    }
}
