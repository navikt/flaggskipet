package no.nav.flaggskipet.bootstrap

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import no.nav.flaggskipet.infrastructure.db.DatabaseConfig
import no.nav.flaggskipet.infrastructure.db.databaseModule
import org.koin.ktor.plugin.koin
import org.koin.logger.slf4jLogger
import org.koin.ktor.plugin.Koin as KoinPlugin

internal fun Application.installDependencyInjection(
    applicationState: ApplicationState,
    databaseConfig: DatabaseConfig,
){
    install(KoinPlugin) {
        slf4jLogger()
        modules(databaseModule(applicationState, databaseConfig))
    }

    monitor.subscribe(ApplicationStopped) {
        koin().get<HikariDataSource>().close()
    }
}
