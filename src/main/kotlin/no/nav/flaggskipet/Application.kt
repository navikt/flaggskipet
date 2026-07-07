package no.nav.flaggskipet

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.di.dependencies
import no.nav.flaggskipet.api.installPlugins
import no.nav.flaggskipet.api.internal.configureInternalApi
import no.nav.flaggskipet.api.tiltakspakker.configureVurderingApi
import no.nav.flaggskipet.bootstrap.installDependencyInjection
import no.nav.flaggskipet.infrastructure.database.config.migrate
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import javax.sql.DataSource
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

@Suppress("unused")
fun Application.module() {
    installPlugins()
    installDependencyInjection()
    val dataSource: DataSource by dependencies
    dataSource.migrate()
    configureVurderingApi()
    configureInternalApi()
}
