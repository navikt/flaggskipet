package no.nav.flaggskipet

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.nav.flaggskipet.api.configureRouting
import no.nav.flaggskipet.api.plugins.installPlugins
import no.nav.flaggskipet.bootstrap.ApplicationState
import no.nav.flaggskipet.bootstrap.configureLifecycleHooks
import no.nav.flaggskipet.bootstrap.installDependencyInjection
import no.nav.flaggskipet.infrastructure.config.AppConfig
import no.nav.flaggskipet.infrastructure.db.DatabaseInitializer
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun main(args: Array<String>) {
    logger.debug("Flaggskipet is starting...")
    try {
        EngineMain.main(args)
    } catch (error: Throwable) {
        logger.error("Flaggskipet failed to start or stopped due to a fatal error", error)
        throw error
    }
}

fun Application.module() {
    val applicationState = ApplicationState()
    configureLifecycleHooks(applicationState)
    installPlugins()
    val appConfig = AppConfig.fromConfig(environment.config)
    installDependencyInjection(applicationState, appConfig)
    val databaseInitializer by inject<DatabaseInitializer>()
    databaseInitializer.migrate()
    configureRouting()
}
