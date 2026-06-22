package no.nav.flaggskipet.infrastructure.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.flaggskipet.infrastructure.db.repositories.InvalidHendelseRepository
import no.nav.flaggskipet.infrastructure.db.repositories.InvalidHendelseRepositoryImpl
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepository
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepositoryImpl
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

fun databaseModule(consumerConfig: DatabaseConfig): Module = module {
    single<HikariDataSource> { createDataSource(consumerConfig) }
    single<DataSource> { get<HikariDataSource>() }
    single { Database.connect(get<DataSource>()) }
    single { HealthIndicator(get()) }
    single { Initializer(get()) }
    single<SykmeldingHendelseRepository> { SykmeldingHendelseRepositoryImpl(get()) }
    single<InvalidHendelseRepository> { InvalidHendelseRepositoryImpl(get()) }
}

suspend fun <T> Database.transact(block: () -> T): T = withContext(Dispatchers.IO) {
    transaction(this@transact) { block() }
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


private val logger = LoggerFactory.getLogger(HealthIndicator::class.java)

class HealthIndicator(
    private val dataSource: DataSource,
) {
    fun isHealthy(): Boolean = try {
        dataSource.connection.use { connection ->
            connection.isValid(1)
        }
    } catch (ex: SQLException) {
        logger.warn("Database health check failed", ex)
        false
    }
}

class Initializer(
    private val dataSource: DataSource,
) {
    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}

