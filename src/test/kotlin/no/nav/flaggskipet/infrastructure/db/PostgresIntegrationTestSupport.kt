package no.nav.flaggskipet.infrastructure.db

import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

internal suspend fun <T> withMigratedPostgres(block: suspend (HikariDataSource, Database) -> T): T {
    return RepoPostgresContainer().use { postgres ->
        postgres
            .withExposedPorts(5432)
            .withDatabaseName("flaggskipet")
            .withUsername("flaggskipet")
            .withPassword("flaggskipet")
        postgres.waitingFor(HostPortWaitStrategy())
        postgres.start()

        createDataSource(
            DatabaseConfig(
                username = postgres.getUsername(),
                password = postgres.getPassword(),
                jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.getDatabaseName()}",
            ),
        ).use { dataSource ->
            DatabaseInitializer(dataSource).migrate()
            block(dataSource, Database.connect(dataSource))
        }
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
