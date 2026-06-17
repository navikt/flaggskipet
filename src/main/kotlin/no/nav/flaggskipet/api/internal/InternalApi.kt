package no.nav.flaggskipet.api.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.flaggskipet.bootstrap.ApplicationState

private const val POD_HEALTH_PATH = "/internal/health"
const val POD_METRICS_PATH = "/internal/metrics"
val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun Routing.registerPodApi(applicationState: ApplicationState) {
    get("$POD_HEALTH_PATH/is_alive") {
        if (applicationState.alive) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get("$POD_HEALTH_PATH/is_ready") {
        if (isReady(applicationState)) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun isReady(applicationState: ApplicationState): Boolean = applicationState.ready

fun Routing.registerMetricApi() {
    get(POD_METRICS_PATH) {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
