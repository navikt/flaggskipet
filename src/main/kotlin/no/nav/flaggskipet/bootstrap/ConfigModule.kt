package no.nav.flaggskipet.bootstrap

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.provide
import no.nav.flaggskipet.infrastructure.clients.ereg.toEregConfig
import no.nav.flaggskipet.infrastructure.db.core.toDatabaseConfig

internal fun DependencyRegistry.configModule(config: ApplicationConfig) {
    provide { config.toDatabaseConfig() }
    provide { config.toEregConfig() }
}
