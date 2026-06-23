package no.nav.flaggskipet.infrastructure.clients.ereg

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig

class ConfigTest :
    FunSpec({
        test("toEregConfig reads base url") {
            config().toEregConfig().baseUrl shouldBe "https://ereg-services.dev.intern.nav.no"
        }

        test("toEregConfig validates blank base url") {
            shouldThrow<IllegalStateException> {
                config(baseUrl = "").toEregConfig()
            }.message shouldBe "Invalid ereg configuration: ereg.baseUrl must be a valid URL"
        }

        test("toEregConfig validates missing base url") {
            shouldThrow<IllegalStateException> {
                MapApplicationConfig().toEregConfig()
            }.message shouldBe "Invalid ereg configuration: ereg.baseUrl must be a valid URL"
        }

        test("toEregConfig validates invalid base url") {
            shouldThrow<IllegalStateException> {
                config(baseUrl = "not-a-url").toEregConfig()
            }.message shouldBe "Invalid ereg configuration: ereg.baseUrl must be a valid URL"
        }
    })

private fun config(baseUrl: String = "https://ereg-services.dev.intern.nav.no"): MapApplicationConfig = MapApplicationConfig(
    "ereg.baseUrl" to baseUrl,
)
