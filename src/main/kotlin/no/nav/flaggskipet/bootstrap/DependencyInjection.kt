package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.nav.flaggskipet.infrastructure.clients.ereg.eregModule
import no.nav.flaggskipet.infrastructure.db.core.databaseModule
import no.nav.flaggskipet.infrastructure.kafka.core.kafkaModule
import org.koin.logger.slf4jLogger
import org.koin.ktor.plugin.Koin as KoinPlugin

internal fun Application.installDependencyInjection() {
    install(KoinPlugin) {
        slf4jLogger()
        modules(
            configModule(environment.config),
            metricsModule(),
            databaseModule(),
            eregModule(),
            kafkaModule(),
        )
    }
}
