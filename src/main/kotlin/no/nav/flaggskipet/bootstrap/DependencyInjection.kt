package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import no.nav.flaggskipet.domain.useCaseModule
import no.nav.flaggskipet.infrastructure.clients.ereg.eregModule
import no.nav.flaggskipet.infrastructure.database.config.databaseModule

internal fun Application.installDependencyInjection() {
    val config = environment.config
    dependencies {
        configModule(config)
        metricsModule()
        databaseModule()
        eregModule()
        useCaseModule()
    }
}
