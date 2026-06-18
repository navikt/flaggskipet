package no.nav.flaggskipet.api.plugins

import io.ktor.server.application.Application
import no.nav.flaggskipet.api.error.installStatusPages

fun Application.installPlugins() {
    installCallId()
    installContentNegotiation()
    installStatusPages()
}
