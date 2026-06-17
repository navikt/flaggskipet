package no.nav.flaggskipet

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val applicationState = ApplicationState()
    val dependencies = installDependencyInjection(applicationState, environment.config)

    dependencies.initializeDatabase()
    configureRouting(applicationState)
}
