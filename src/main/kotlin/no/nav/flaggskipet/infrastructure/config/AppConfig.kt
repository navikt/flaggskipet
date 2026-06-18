package no.nav.flaggskipet.infrastructure.config

import io.ktor.server.config.ApplicationConfig
import no.nav.flaggskipet.infrastructure.db.DatabaseConfig

// Aggregates every config group, built once at startup so each group's validation fails fast.
// Add new groups here (e.g. texas) as TexasConfig.fromConfig(config).
data class AppConfig(
    val database: DatabaseConfig,
) {
    companion object {
        fun fromConfig(config: ApplicationConfig): AppConfig = AppConfig(
            database = DatabaseConfig.fromConfig(config),
        )
    }
}
