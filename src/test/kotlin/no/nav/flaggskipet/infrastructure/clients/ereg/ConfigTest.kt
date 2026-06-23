package no.nav.flaggskipet.infrastructure.clients.ereg

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig
class ConfigTest :
    FunSpec({

        test("toEregConfig reads base url") {
            config().toEregConfig().baseUrl shouldBe
                "https://ereg-services.dev.intern.nav.no"
        }

        test("toEregConfig rejects invalid base url") {
            shouldThrow<IllegalArgumentException> {
                config(baseUrl = "not-a-url").toEregConfig()
            }
        }
    })

private fun config(
    baseUrl: String = "https://ereg-services.dev.intern.nav.no",
) = MapApplicationConfig(
    "ereg.baseUrl" to baseUrl,
)
