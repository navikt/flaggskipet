package no.nav.flaggskipet.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import no.nav.flaggskipet.ApplicationState
import org.koin.core.module.Module
import org.koin.dsl.module
import javax.sql.DataSource

fun databaseModule(
    applicationState: ApplicationState,
    config: ApplicationConfig,
): Module = module {
    single { applicationState }
    single { DatabaseConfig.fromConfig(config) }
    single<HikariDataSource> { createDataSource(get()) }
    single<DataSource> { get<HikariDataSource>() }
    single { DatabaseInitializer(get()) }
}

fun createDataSource(config: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = config.jdbcUrl()
        username = config.username
        password = config.password
        maximumPoolSize = 3
        minimumIdle = 1
        connectionTimeout = 10_000
        idleTimeout = 300_000
        maxLifetime = 1_800_000
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        validate()
    },
)
