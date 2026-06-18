package no.nav.flaggskipet.infrastructure.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.config.MapApplicationConfig

class DatabaseConfigTest :
    FunSpec({
        test("fromConfig reads database properties") {
            with(
                DatabaseConfig.fromConfig(config()),
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
                    config(url = "postgresql://dbhost:5432/flaggskipet?user=flaggskipet&password=supersecret"),
                ),
            ) {
                jdbcUrl shouldBe "jdbc:postgresql://dbhost:5432/flaggskipet?user=flaggskipet&password=supersecret"
            }
        }

        test("fromConfig reports all missing required database properties") {
            with(
                shouldThrow<IllegalStateException> {
                    DatabaseConfig.fromConfig(
                        config(
                            port = "",
                            name = "",
                            username = "",
                            password = "",
                            url = "",
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
                    DatabaseConfig.fromConfig(config(port = "not-a-number"))
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
    })

private fun config(
    host: String = "localhost",
    port: String = "5432",
    name: String = "flaggskipet",
    username: String = "flaggskipet",
    password: String = "supersecret",
    url: String = "postgresql://localhost:5432/flaggskipet",
): MapApplicationConfig = MapApplicationConfig(
    "database.host" to host,
    "database.port" to port,
    "database.name" to name,
    "database.username" to username,
    "database.password" to password,
    "database.url" to url,
)
