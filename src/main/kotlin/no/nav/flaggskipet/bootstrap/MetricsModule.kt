package no.nav.flaggskipet.bootstrap

import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.provide
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

internal fun DependencyRegistry.metricsModule() {
    provide { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
}
