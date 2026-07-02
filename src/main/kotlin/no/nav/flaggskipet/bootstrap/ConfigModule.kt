package no.nav.flaggskipet.bootstrap

import io.ktor.server.config.ApplicationConfig
import no.nav.flaggskipet.infrastructure.clients.ereg.toEregConfig
import no.nav.flaggskipet.infrastructure.db.core.toDatabaseConfig
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun configModule(config: ApplicationConfig): Module = module {
    single { config.toDatabaseConfig() }
    single { config.toEregConfig() }
}
