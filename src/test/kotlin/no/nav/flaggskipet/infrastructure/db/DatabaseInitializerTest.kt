package no.nav.flaggskipet.infrastructure.db

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.ktor.server.config.MapApplicationConfig
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

class DatabaseInitializerTest :
    FunSpec({
        test("flyway migrates postgres 18 and keeps migration history idempotent") {
            PsqlContainer().use { postgres ->
                postgres
                    .withExposedPorts(5432)
                    .withDatabaseName("flaggskipet")
                    .withUsername("flaggskipet")
                    .withPassword("flaggskipet")
                postgres.waitingFor(HostPortWaitStrategy())
                postgres.start()

                createDataSource(
                    DatabaseConfig.fromConfig(
                        MapApplicationConfig(
                            "database.host" to postgres.host,
                            "database.port" to postgres.getMappedPort(5432).toString(),
                            "database.name" to postgres.databaseName,
                            "database.username" to postgres.username,
                            "database.password" to postgres.password,
                        ),
                    ),
                ).use { dataSource ->
                    val initializer = DatabaseInitializer(dataSource)

                    initializer.initialize()

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

                    initializer.initialize()

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
