package no.nav.flaggskipet.infrastructure.db

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.MapApplicationConfig

class DatabaseConfigTest :
    FunSpec({
        test("application.conf builds local fallback jdbc url") {
            with(
                DatabaseConfig.fromConfig(
                    HoconApplicationConfig(
                        ConfigFactory
                            .parseString(
                                """
                                database.host = "localhost"
                                database.port = 5432
                                database.name = "flaggskipet"
                                database.username = "flaggskipet"
                                database.password = "supersecret"
                                """.trimIndent(),
                            )
                            .withFallback(ConfigFactory.load("application.conf"))
                            .resolve(),
                    ),
                ),
            ) {
                jdbcUrl shouldBe "jdbc:postgresql://localhost:5432/flaggskipet"
            }
        }

        test("fromConfig reads database properties") {
            with(
                DatabaseConfig.fromConfig(
                    MapApplicationConfig(
                        "database.host" to "localhost",
                        "database.port" to "5432",
                        "database.name" to "flaggskipet",
                        "database.username" to "flaggskipet",
                        "database.password" to "supersecret",
                        "database.url" to "postgresql://localhost:5432/flaggskipet",
                    ),
                ),
            ) {
                host shouldBe "localhost"
                port shouldBe 5432
                database shouldBe "flaggskipet"
                username shouldBe "flaggskipet"
                jdbcUrl shouldBe "jdbc:postgresql://localhost:5432/flaggskipet"
            }
        }

        test("fromConfig prefixes configured database url for jdbc") {
            with(
                DatabaseConfig.fromConfig(
                    MapApplicationConfig(
                        "database.host" to "localhost",
                        "database.port" to "5432",
                        "database.name" to "flaggskipet",
                        "database.username" to "flaggskipet",
                        "database.password" to "supersecret",
                        "database.url" to "postgresql://dbhost:5432/flaggskipet?user=flaggskipet&password=supersecret",
                    ),
                ),
            ) {
                jdbcUrl shouldBe "jdbc:postgresql://dbhost:5432/flaggskipet?user=flaggskipet&password=supersecret"
            }
        }

        test("fromConfig reports all missing required database properties") {
            with(
                shouldThrow<IllegalStateException> {
                    DatabaseConfig.fromConfig(
                        MapApplicationConfig(
                            "database.host" to "localhost",
                            "database.port" to "",
                        ),
                    )
                },
            ) {
                message shouldBe "Invalid database configuration: database.port must be set, database.name must be set, database.username must be set, database.password must be set, database.url must be set"
            }
        }

        test("fromConfig validates database port") {
            with(
                shouldThrow<IllegalStateException> {
                    DatabaseConfig.fromConfig(
                        MapApplicationConfig(
                            "database.host" to "localhost",
                            "database.port" to "not-a-number",
                            "database.name" to "flaggskipet",
                            "database.username" to "flaggskipet",
                            "database.password" to "supersecret",
                            "database.url" to "postgresql://localhost:5432/flaggskipet",
                        ),
                    )
                },
            ) {
                message shouldBe "Invalid database configuration: database.port must be a positive integer"
            }
        }

        test("toString masks password") {
            with(
                DatabaseConfig(
                    host = "localhost",
                    port = 5432,
                    database = "flaggskipet",
                    username = "flaggskipet",
                    password = "supersecret",
                    jdbcUrl = "jdbc:postgresql://localhost:5432/flaggskipet",
                ),
            ) {
                toString().shouldContain("password=***")
                toString().shouldNotContain("supersecret")
            }
        }

        test("jdbcUrl is masked from toString") {
            with(
                DatabaseConfig(
                    host = "dbhost",
                    port = 5432,
                    database = "flaggskipet",
                    username = "flaggskipet",
                    password = "supersecret",
                    jdbcUrl = "jdbc:postgresql://dbhost:5432/flaggskipet?user=flaggskipet&password=supersecret&sslmode=verify-ca",
                ),
            ) {
                jdbcUrl shouldBe "jdbc:postgresql://dbhost:5432/flaggskipet?user=flaggskipet&password=supersecret&sslmode=verify-ca"
                toString().shouldNotContain("supersecret")
            }
        }
    })
