package no.nav.flaggskipet.api.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.flaggskipet.infrastructure.HealthCheck

private const val POD_HEALTH_PATH = "/internal/health"
const val POD_METRICS_PATH = "/internal/metrics"

fun Application.configureInternalApi() {
    val healthCheck: HealthCheck by dependencies
    val meterRegistry: PrometheusMeterRegistry by dependencies

    routing {
        registerPodApi(healthCheck)
        registerMetricApi(meterRegistry)
    }
}

fun Routing.registerPodApi(
    healthCheck: HealthCheck,
) {
    get("$POD_HEALTH_PATH/is_alive") {
        call.respondText("I'm alive! :)")
    }
    get("$POD_HEALTH_PATH/is_ready") {
        val result = healthCheck.check()
        if (result.healthy) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText(
                result.message,
                status = HttpStatusCode.ServiceUnavailable,
            )
        }
    }
}

fun Routing.registerMetricApi(meterRegistry: PrometheusMeterRegistry) {
    get(POD_METRICS_PATH) {
        call.respondText(meterRegistry.scrape())
    }
}
