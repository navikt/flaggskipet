package no.nav.flaggskipet.infrastructure.config

import io.ktor.server.config.ApplicationConfig
import no.nav.flaggskipet.infrastructure.db.core.DatabaseConfig
import no.nav.flaggskipet.infrastructure.kafka.core.KafkaConfig

// Aggregates every config group, built once at startup so each group's validation fails fast.
// Add new groups here (e.g. texas) as TexasConfig.fromConfig(config).
data class AppConfig(
    val database: DatabaseConfig,
    val kafka: KafkaConfig,
) {
    companion object {
        fun fromConfig(config: ApplicationConfig): AppConfig = AppConfig(
            database = DatabaseConfig.fromConfig(config),
            kafka = KafkaConfig.fromConfig(config),
        )
    }
}
