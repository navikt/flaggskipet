package no.nav.flaggskipet.api

import io.ktor.server.application.Application
import no.nav.flaggskipet.api.internal.configureInternalApi

fun Application.configureRouting() {
    configureInternalApi()
}
