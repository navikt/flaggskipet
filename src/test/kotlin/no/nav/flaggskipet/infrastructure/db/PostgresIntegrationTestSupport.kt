package no.nav.flaggskipet.infrastructure.db

import com.zaxxer.hikari.HikariDataSource
import no.nav.flaggskipet.infrastructure.db.core.DatabaseConfig
import no.nav.flaggskipet.infrastructure.db.core.createDataSource
import no.nav.flaggskipet.infrastructure.db.core.migrate
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

internal suspend fun <T> withMigratedPostgres(block: suspend (HikariDataSource, Database) -> T): T = RepoPostgresContainer().use { postgres ->
    postgres
        .withExposedPorts(5432)
        .withDatabaseName("flaggskipet")
        .withUsername("flaggskipet")
        .withPassword("flaggskipet")
    postgres.waitingFor(HostPortWaitStrategy())
    postgres.start()

    createDataSource(
        DatabaseConfig(
            username = postgres.username,
            password = postgres.password,
            jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.getDatabaseName()}",
        ),
    ).use { dataSource ->
        dataSource.migrate()
        block(dataSource, Database.connect(dataSource))
    }
}

private class RepoPostgresContainer : PostgreSQLContainer<RepoPostgresContainer>("postgres:18-alpine")

internal fun HikariDataSource.queryForInt(sql: String): Int = connection.use { connection ->
    connection.prepareStatement(sql).use { statement ->
        statement.executeQuery().use { resultSet ->
            resultSet.next()
            resultSet.getInt(1)
        }
    }
}
