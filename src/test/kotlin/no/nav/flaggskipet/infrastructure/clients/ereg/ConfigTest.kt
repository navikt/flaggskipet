package no.nav.flaggskipet.infrastructure.clients.ereg

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig
import java.net.URI

class ConfigTest :
    FunSpec({
        test("toEregConfig reads valid base url") {
            config().toEregConfig() shouldBe EregConfig(
                baseUrl = URI("https://ereg-services.dev.intern.nav.no"),
            )
        }

        test("toEregConfig rejects missing or blank base url") {
            shouldThrow<IllegalStateException> {
                config(baseUrl = "").toEregConfig()
            }.message shouldBe "Invalid ereg configuration: ereg.baseUrl must be set"

            shouldThrow<IllegalStateException> {
                MapApplicationConfig().toEregConfig()
            }.message shouldBe "Invalid ereg configuration: ereg.baseUrl must be set"
        }

        test("toEregConfig rejects invalid or relative base url") {
            shouldThrow<IllegalStateException> {
                config(baseUrl = "not-a-url").toEregConfig()
            }.message shouldBe "Invalid ereg configuration: ereg.baseUrl must be a valid URL"
        }
    })

private fun config(baseUrl: String = "https://ereg-services.dev.intern.nav.no"): MapApplicationConfig = MapApplicationConfig(
    "ereg.baseUrl" to baseUrl,
)
