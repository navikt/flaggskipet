package no.nav.flaggskipet

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.nav.flaggskipet.bootstrap.ApplicationState
import no.nav.flaggskipet.bootstrap.configureLifecycleHooks
import no.nav.flaggskipet.bootstrap.configureRouting
import no.nav.flaggskipet.bootstrap.installDependencyInjection
import no.nav.flaggskipet.infrastructure.db.DatabaseInitializer
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import org.koin.ktor.ext.inject

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
    installDependencyInjection(applicationState, environment.config)
    val databaseInitializer by inject<DatabaseInitializer>()
    databaseInitializer.initialize()
    configureRouting()
}
