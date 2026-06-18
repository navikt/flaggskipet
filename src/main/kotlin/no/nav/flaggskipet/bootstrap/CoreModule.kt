package no.nav.flaggskipet.bootstrap

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.core.module.Module
import org.koin.dsl.module

// App-wide singletons that are not tied to a specific infrastructure concern.
fun coreModule(applicationState: ApplicationState): Module = module {
    single { applicationState }
    single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
}
