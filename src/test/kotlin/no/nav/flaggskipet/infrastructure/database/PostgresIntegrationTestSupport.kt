package no.nav.flaggskipet.infrastructure.database

import com.zaxxer.hikari.HikariDataSource
import no.nav.flaggskipet.infrastructure.database.config.DatabaseConfig
import no.nav.flaggskipet.infrastructure.database.config.createDataSource
import no.nav.flaggskipet.infrastructure.database.config.migrate
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.ResultSet

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

internal fun HikariDataSource.queryForInt(hardcodedSqlQuery: String): Int = queryForValue(hardcodedSqlQuery) { it.getInt(1) }

internal fun HikariDataSource.queryForString(hardcodedSqlQuery: String): String = queryForValue(hardcodedSqlQuery) { it.getString(1) }

private fun <T> HikariDataSource.queryForValue(
    hardcodedSqlQuery: String,
    mapRow: (ResultSet) -> T,
): T = connection.use { connection ->
    connection.prepareStatement(hardcodedSqlQuery).use { statement ->
        statement.executeQuery().use { resultSet ->
            check(resultSet.next()) {
                "Expected query to return at least one row"
            }
            mapRow(resultSet)
        }
    }
}
