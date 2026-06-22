package no.nav.flaggskipet.infrastructure.db.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.config.MapApplicationConfig

class ConfigTest :
    FunSpec({
        test("fromConfig reads database properties") {
            with(
                DatabaseConfig.fromConfig(config()),
            ) {
                username shouldBe "order"
                jdbcUrl shouldBe "jdbc:postgresql://localhost:5432/order"
            }
        }

        test("fromConfig converts configured database url to jdbc url") {
            with(
                DatabaseConfig.fromConfig(
                    config(url = "postgresql://order:supersecret@dbhost:5432/order?sslmode=verify-ca"),
                ),
            ) {
                jdbcUrl shouldBe "jdbc:postgresql://dbhost:5432/order?sslmode=verify-ca"
            }
        }

        test("fromConfig overrides sslkey with the pk8 key path") {
            with(
                DatabaseConfig.fromConfig(
                    config(
                        url = "postgresql://order:supersecret@dbhost:5432/order" +
                            "?sslcert=/secrets/cert.pem&sslkey=/secrets/key.pem&sslmode=verify-ca",
                        sslkey = "/secrets/key.pk8",
                    ),
                ),
            ) {
                jdbcUrl shouldBe "jdbc:postgresql://dbhost:5432/order" +
                    "?sslcert=/secrets/cert.pem&sslkey=/secrets/key.pk8&sslmode=verify-ca"
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
                    username = "order",
                    password = "supersecret",
                    jdbcUrl = "jdbc:postgresql://localhost:5432/order",
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
    name: String = "order",
    username: String = "order",
    password: String = "supersecret",
    url: String = "postgresql://localhost:5432/order",
    sslkey: String = "",
): MapApplicationConfig = MapApplicationConfig(
    "database.host" to host,
    "database.port" to port,
    "database.name" to name,
    "database.username" to username,
    "database.password" to password,
    "database.url" to url,
    "database.sslkey" to sslkey,
)
