package no.nav.flaggskipet.infrastructure.db

import com.zaxxer.hikari.HikariDataSource
import no.nav.flaggskipet.infrastructure.db.core.DatabaseConfig
import no.nav.flaggskipet.infrastructure.db.core.createDataSource
import no.nav.flaggskipet.infrastructure.db.core.migrate
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer

internal suspend fun <T> withMigratedPostgres(block: suspend (HikariDataSource, Database) -> T): T = RepoPostgresContainer().use { postgres ->
    postgres
        .withDatabaseName("flaggskipet")
        .withUsername("flaggskipet")
        .withPassword("flaggskipet")
    postgres.start()

    createDataSource(
        DatabaseConfig(
            username = postgres.username,
            password = postgres.password,
            jdbcUrl = postgres.jdbcUrl,
        ),
    ).use { dataSource ->
        dataSource.migrate()
        block(dataSource, Database.connect(dataSource))
    }
}

private class RepoPostgresContainer : PostgreSQLContainer<RepoPostgresContainer>("postgres:18-alpine")

@Suppress("SqlSourceToSinkFlow")
internal fun HikariDataSource.queryForInt(hardcodedSqlQuery: String): Int = connection.use { connection ->
    connection.prepareStatement(hardcodedSqlQuery).use { statement ->
        statement.executeQuery().use { resultSet ->
            resultSet.next()
            resultSet.getInt(1)
        }
    }
}
