package no.nav.flaggskipet.infrastructure.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.flaggskipet.infrastructure.db.repositories.InvalidHendelseRepository
import no.nav.flaggskipet.infrastructure.db.repositories.InvalidHendelseRepositoryImpl
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepository
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepositoryImpl
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepositoryImpl
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

fun databaseModule(): Module = module {
    single<HikariDataSource> { createDataSource(get()) } onClose {
        it?.close()
    }
    single<DataSource> { get<HikariDataSource>() }
    single { Database.connect(get<DataSource>()) }
    single<SykmeldingHendelseRepository> { SykmeldingHendelseRepositoryImpl(get()) }
    single<InvalidHendelseRepository> { InvalidHendelseRepositoryImpl(get()) }
    single<TiltakspakkeVurderingRepository> { TiltakspakkeVurderingRepositoryImpl(get()) }
}

suspend fun <T> Database.transact(block: () -> T): T = withContext(Dispatchers.IO) {
    transaction(this@transact) { block() }
}

fun createDataSource(databaseConfig: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = databaseConfig.jdbcUrl
        username = databaseConfig.username
        password = databaseConfig.password
        maximumPoolSize = 3
        minimumIdle = 1
        connectionTimeout = 10_000
        idleTimeout = 300_000
        maxLifetime = 1_800_000
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        validate()
    },
)

private val logger = LoggerFactory.getLogger("no.nav.flaggskipet.infrastructure.db.core.DatabaseHealth")

fun DataSource.isHealthy(): Boolean = try {
    connection.use { dbConnection ->
        dbConnection.isValid(1)
    }
} catch (ex: SQLException) {
    logger.error("Database health check failed", ex)
    false
}

fun DataSource.migrate() {
    Flyway.configure()
        .dataSource(this)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}
