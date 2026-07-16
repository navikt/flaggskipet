package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.flaggskipet.infrastructure.clients.ereg.eregModule
import no.nav.flaggskipet.infrastructure.database.config.databaseModule

internal fun Application.installDependencyInjection() {
    val config = environment.config
    dependencies {
        configModule(config)
        provide { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
        databaseModule()
        eregModule()
        useCaseModule()
    }
}
