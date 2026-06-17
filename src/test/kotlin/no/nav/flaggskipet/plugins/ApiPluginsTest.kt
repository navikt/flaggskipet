package no.nav.flaggskipet.plugins

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.Application
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class ApiPluginsTest :
    FunSpec({
        test("status pages responds with api error for not found exception") {
            testApplication {
                application {
                    installPluginTestRoutes()
                }

                val response = client.get("/not-found")

                response.status shouldBe HttpStatusCode.NotFound
                response.headers["Content-Type"] shouldBe "application/json"
                with(response.bodyAsText()) {
                    shouldContain(""""status":404""")
                    shouldContain(""""type":"NOT_FOUND"""")
                    shouldContain(""""message":"missing resource"""")
                    shouldContain(""""path":"/not-found"""")
                    shouldContain(""""timestamp":"""")
                }
            }
        }
    })

private fun Application.installPluginTestRoutes() {
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        get("/not-found") {
            throw NotFoundException("missing resource")
        }
        get("/ok") {
            call.respondText("ok")
        }
    }
}
