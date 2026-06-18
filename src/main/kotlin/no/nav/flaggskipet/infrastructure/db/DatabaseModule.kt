package no.nav.flaggskipet.infrastructure.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module
import javax.sql.DataSource

fun databaseModule(databaseConfig: DatabaseConfig): Module = module {
    single<HikariDataSource> { createDataSource(databaseConfig) }
    single<DataSource> { get<HikariDataSource>() }
    single { Database.connect(get<DataSource>()) }
    single { DatabaseHealthIndicator(get()) }
    single { DatabaseInitializer(get()) }
}

fun createDataSource(config: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = config.jdbcUrl
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
