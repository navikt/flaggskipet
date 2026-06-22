package no.nav.flaggskipet.infrastructure.db.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeExactly
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

class MigrationTest :
    FunSpec({
        test("flyway migrates postgres 18 and keeps migration history idempotent") {
            PsqlContainer().use { postgres ->
                postgres
                    .withExposedPorts(5432)
                    .withDatabaseName("order")
                    .withUsername("order")
                    .withPassword("order")
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

                    dataSource.connection.use { connection ->
                        connection.isValid(2).shouldBeTrue()

                        connection
                            .createStatement()
                            .use { statement ->
                                statement
                                    .executeQuery(
                                        """
                                        SELECT success
                                        FROM flyway_schema_history
                                        WHERE version = '1'
                                        """.trimIndent(),
                                    ).use { resultSet ->
                                        resultSet.next().shouldBeTrue()
                                        resultSet.getBoolean("success").shouldBeTrue()
                                    }
                            }
                    }

                    dataSource.migrate()

                    dataSource.connection.use { connection ->
                        connection
                            .createStatement()
                            .use { statement ->
                                statement
                                    .executeQuery(
                                        """
                                        SELECT COUNT(*) AS successful_baselines
                                        FROM flyway_schema_history
                                        WHERE version = '1'
                                          AND success = true
                                        """.trimIndent(),
                                    ).use { resultSet ->
                                        resultSet.next().shouldBeTrue()
                                        resultSet.getInt("successful_baselines").shouldBeExactly(1)
                                    }
                            }
                    }
                }
            }
        }
    })

private class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:18-alpine")
