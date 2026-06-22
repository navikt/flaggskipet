package no.nav.flaggskipet.bootstrap

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.flaggskipet.infrastructure.db.core.DatabaseConfig
import no.nav.flaggskipet.infrastructure.db.core.databaseModule
import no.nav.flaggskipet.infrastructure.kafka.core.KafkaConfig
import no.nav.flaggskipet.infrastructure.kafka.core.kafkaModule
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.logger.slf4jLogger
import org.koin.ktor.plugin.Koin as KoinPlugin

internal fun Application.installDependencyInjection() {
    install(KoinPlugin) {
        slf4jLogger()
        modules(
            module {
                single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
            },
            databaseModule(DatabaseConfig.fromConfig(environment.config)),
            kafkaModule(KafkaConfig.fromConfig(environment.config)),
        )
    }

    monitor.subscribe(ApplicationStopped) {
        get<HikariDataSource>().close()
    }
}
