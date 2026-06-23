package no.nav.flaggskipet.bootstrap

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun metricsModule(): Module = module {
    single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
}
