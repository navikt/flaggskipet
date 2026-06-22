package no.nav.flaggskipet.infrastructure.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.flaggskipet.infrastructure.db.repositories.InvalidHendelseRepository
import no.nav.flaggskipet.infrastructure.db.repositories.InvalidHendelseRepositoryImpl
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepository
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepositoryImpl
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module
import javax.sql.DataSource

fun databaseModule(consumerConfig: DatabaseConfig): Module = module {
    single<HikariDataSource> { createDataSource(consumerConfig) }
    single<DataSource> { get<HikariDataSource>() }
    single { Database.connect(get<DataSource>()) }
    single { Transaction(get()) }
    single { HealthIndicator(get()) }
    single { Initializer(get()) }
    single<SykmeldingHendelseRepository> { SykmeldingHendelseRepositoryImpl(get()) }
    single<InvalidHendelseRepository> { InvalidHendelseRepositoryImpl(get()) }
}

fun createDataSource(consumerConfig: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = consumerConfig.jdbcUrl
        username = consumerConfig.username
        password = consumerConfig.password
        maximumPoolSize = 3
        minimumIdle = 1
        connectionTimeout = 10_000
        idleTimeout = 300_000
        maxLifetime = 1_800_000
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        validate()
    },
)
