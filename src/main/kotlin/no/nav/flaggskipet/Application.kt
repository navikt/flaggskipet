package no.nav.flaggskipet

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.nav.flaggskipet.api.installPlugins
import no.nav.flaggskipet.api.internal.configureInternalApi
import no.nav.flaggskipet.bootstrap.ApplicationState
import no.nav.flaggskipet.bootstrap.configureLifecycleHooks
import no.nav.flaggskipet.bootstrap.installDependencyInjection
import no.nav.flaggskipet.bootstrap.startKafkaConsumers
import no.nav.flaggskipet.infrastructure.db.core.Initializer
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun main(args: Array<String>) {
    logger.debug("Flaggskipet is starting...")
    try {
        EngineMain.main(args)
    } catch (error: Throwable) {
        logger.error("Flaggskipet failed to start or stopped due to a fatal error", error)
        exitProcess(1)
    }
}

fun Application.module() {
    val applicationState = ApplicationState()
    configureLifecycleHooks(applicationState)
    installPlugins()
    installDependencyInjection()
    get<Initializer>().migrate()
    startKafkaConsumers(applicationState)
    configureInternalApi(applicationState)
}
