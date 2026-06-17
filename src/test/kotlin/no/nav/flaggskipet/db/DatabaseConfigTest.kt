package no.nav.flaggskipet.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.config.MapApplicationConfig

class DatabaseConfigTest :
    FunSpec({
        test("fromConfig reads database properties") {
            val config =
                DatabaseConfig.fromConfig(
                    MapApplicationConfig(
                        "database.host" to "localhost",
                        "database.port" to "5432",
                        "database.name" to "flaggskipet",
                        "database.username" to "flaggskipet",
                        "database.password" to "supersecret",
                    ),
                )

            config.host shouldBe "localhost"
            config.port shouldBe 5432
            config.database shouldBe "flaggskipet"
            config.username shouldBe "flaggskipet"
            config.toString().shouldContain("password=***")
            config.toString().shouldNotContain("supersecret")
        }

        test("fromConfig requires database properties") {
            val error =
                shouldThrow<IllegalStateException> {
                    DatabaseConfig.fromConfig(
                        MapApplicationConfig(
                            "database.host" to "localhost",
                            "database.port" to "5432",
                            "database.name" to "flaggskipet",
                            "database.username" to "flaggskipet",
                        ),
                    )
                }

            error.message shouldBe "database.password must be set"
        }

        test("fromConfig validates database port") {
            val error =
                shouldThrow<IllegalStateException> {
                    DatabaseConfig.fromConfig(
                        MapApplicationConfig(
                            "database.host" to "localhost",
                            "database.port" to "not-a-number",
                            "database.name" to "flaggskipet",
                            "database.username" to "flaggskipet",
                            "database.password" to "supersecret",
                        ),
                    )
                }

            error.message shouldBe "database.port must be an integer"
        }

        test("toString masks password") {
            val config =
                DatabaseConfig(
                    host = "localhost",
                    port = 5432,
                    database = "flaggskipet",
                    username = "flaggskipet",
                    password = "supersecret",
                )

            config.toString().shouldContain("password=***")
            config.toString().shouldNotContain("supersecret")
        }
    })
