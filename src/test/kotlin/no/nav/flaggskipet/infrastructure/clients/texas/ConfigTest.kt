package no.nav.flaggskipet.infrastructure.clients.texas

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig
import java.net.URI

class ConfigTest :
    FunSpec({
        test("toTexasConfig leser gyldig introspection endpoint") {
            config().toTexasConfig() shouldBe TexasConfig(
                introspectionEndpoint = URI("http://localhost:3000/api/v1/introspect"),
            )
        }

        test("toTexasConfig avviser manglende eller tomt introspection endpoint") {
            shouldThrow<IllegalStateException> {
                config(introspectionEndpoint = "").toTexasConfig()
            }.message shouldBe "Invalid texas configuration: texas.introspectionEndpoint must be set"

            shouldThrow<IllegalStateException> {
                MapApplicationConfig().toTexasConfig()
            }.message shouldBe "Invalid texas configuration: texas.introspectionEndpoint must be set"
        }
    })

private fun config(introspectionEndpoint: String = "http://localhost:3000/api/v1/introspect"): MapApplicationConfig = MapApplicationConfig(
    "texas.introspectionEndpoint" to introspectionEndpoint,
)
