package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import no.nav.flaggskipet.api.error.installStatusPages
import no.nav.flaggskipet.api.internal.configureInternalApi
import no.nav.flaggskipet.api.plugins.installCallId
import no.nav.flaggskipet.api.plugins.installContentNegotiation

fun Application.configureRouting() {
    installCallId()
    installContentNegotiation()
    installStatusPages()
    configureInternalApi()
}
