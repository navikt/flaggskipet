package no.nav.flaggskipet

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("no.nav.flaggskipet.ApplicationKt")

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
    val dependencies = installDependencyInjection(applicationState, environment.config)
    dependencies.initializeDatabase()
    configureRouting(applicationState)
}
