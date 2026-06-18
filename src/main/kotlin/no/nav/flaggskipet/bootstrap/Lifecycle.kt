package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        log.info("Application is ready, running Java VM {}", Runtime.version())
    }
    monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
        log.info("Application is stopped")
    }
}
