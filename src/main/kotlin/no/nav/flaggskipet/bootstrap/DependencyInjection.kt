package no.nav.flaggskipet.bootstrap

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import no.nav.flaggskipet.infrastructure.db.DatabaseInitializer
import no.nav.flaggskipet.infrastructure.db.databaseModule
import org.koin.core.Koin
import org.koin.ktor.plugin.koin
import org.koin.logger.slf4jLogger
import java.util.concurrent.atomic.AtomicBoolean
import org.koin.ktor.plugin.Koin as KoinPlugin

internal fun Application.installDependencyInjection(
    applicationState: ApplicationState,
    config: ApplicationConfig,
): ApplicationDependencies {
    install(KoinPlugin) {
        slf4jLogger()
        modules(databaseModule(applicationState, config))
    }

    return ApplicationDependencies.create(koin()).also { dependencies ->
        closeDependenciesOnShutdown(dependencies)
    }
}

private fun Application.closeDependenciesOnShutdown(dependencies: ApplicationDependencies) {
    monitor.subscribe(ApplicationStopped) {
        dependencies.close()
    }
}

internal class ApplicationDependencies private constructor(
    private val koin: Koin,
    private val dataSource: HikariDataSource,
) {
    private val closed = AtomicBoolean(false)

    companion object {
        fun create(koin: Koin): ApplicationDependencies = runCatching {
            ApplicationDependencies(koin, koin.get())
        }.onFailure {
            koin.close()
        }.getOrThrow()
    }

    fun initializeDatabase() = closeOnFailure {
        koin.get<DatabaseInitializer>().initialize()
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        dataSource.close()
        koin.close()
    }

    private inline fun closeOnFailure(block: () -> Unit) = runCatching(block).onFailure {
        close()
    }.getOrThrow()
}
