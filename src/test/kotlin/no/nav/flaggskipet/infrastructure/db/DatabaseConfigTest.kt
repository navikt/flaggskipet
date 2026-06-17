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
                DatabaseConfig.fromConfig(
                    MapApplicationConfig(
                        "database.host" to "localhost",
                        "database.port" to "5432",
                        "database.name" to "flaggskipet",
                        "database.username" to "flaggskipet",
                        "database.password" to "supersecret",
                    ),
                ),
            ) {
                host shouldBe "localhost"
                port shouldBe 5432
                database shouldBe "flaggskipet"
                username shouldBe "flaggskipet"
                toString().shouldContain("password=***")
                toString().shouldNotContain("supersecret")
            }
        }

        test("fromConfig requires database properties") {
            with(
                shouldThrow<IllegalStateException> {
                    DatabaseConfig.fromConfig(
                        MapApplicationConfig(
                            "database.host" to "localhost",
                            "database.port" to "5432",
                            "database.name" to "flaggskipet",
                            "database.username" to "flaggskipet",
                        ),
                    )
                },
            ) {
                message shouldBe "database.password must be set"
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
                        ),
                    )
                },
            ) {
                message shouldBe "database.port must be an integer"
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
                ),
            ) {
                toString().shouldContain("password=***")
                toString().shouldNotContain("supersecret")
            }
        }
    })
