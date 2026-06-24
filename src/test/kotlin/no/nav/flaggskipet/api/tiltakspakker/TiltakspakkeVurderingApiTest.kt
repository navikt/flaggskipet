package no.nav.flaggskipet.api.tiltakspakker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.api.installPlugins
import no.nav.flaggskipet.infrastructure.db.repositories.Deltakelse
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurdering
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.infrastructure.db.repositories.VirksomhetDeltakelse

class TiltakspakkeVurderingApiTest :
    FunSpec({
        test("get returns vurdering for orgnummer") {
            val repository = FakeTiltakspakkeVurderingRepository(
                result = listOf(
                    TiltakspakkeVurdering(
                        id = "PAKKE_1",
                        virksomheter = listOf(
                            VirksomhetDeltakelse(
                                orgnummer = "123456789",
                                deltakelse = Deltakelse.DELTAR,
                            ),
                        ),
                    ),
                ),
            )

            testApplication {
                application {
                    installTiltakspakkeApi(repository)
                }

                val response = client.get("/api/v1/tiltakspakker/vurdering/123456789")

                response.status shouldBe HttpStatusCode.OK
                Json.decodeFromString<TiltakspakkeVurderingResponse>(response.bodyAsText()) shouldBe
                    TiltakspakkeVurderingResponse(
                        tiltakspakker = listOf(
                            TiltakspakkeResponse(
                                id = "PAKKE_1",
                                virksomheter = listOf(
                                    VirksomhetResponse(
                                        orgnummer = "123456789",
                                        deltakelse = "DELTAR",
                                    ),
                                ),
                            ),
                        ),
                    )
                repository.requests shouldBe listOf(listOf("123456789"))
            }
        }

        test("post returns vurdering for normalized orgnumre") {
            val repository = FakeTiltakspakkeVurderingRepository(
                result = listOf(
                    TiltakspakkeVurdering(
                        id = "SYKEFRAVAERSOPPFOLGING",
                        virksomheter = listOf(
                            VirksomhetDeltakelse(
                                orgnummer = "123456789",
                                deltakelse = Deltakelse.DELTAR,
                            ),
                            VirksomhetDeltakelse(
                                orgnummer = "987654321",
                                deltakelse = Deltakelse.DELTAR_IKKE,
                            ),
                        ),
                    ),
                ),
            )

            testApplication {
                application {
                    installTiltakspakkeApi(repository)
                }

                val response = client.post("/api/v1/tiltakspakker/vurdering") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"orgnumre":["123456789","987654321","123456789"]}""")
                }

                response.status shouldBe HttpStatusCode.OK
                Json.decodeFromString<TiltakspakkeVurderingResponse>(response.bodyAsText()) shouldBe
                    TiltakspakkeVurderingResponse(
                        tiltakspakker = listOf(
                            TiltakspakkeResponse(
                                id = "SYKEFRAVAERSOPPFOLGING",
                                virksomheter = listOf(
                                    VirksomhetResponse(
                                        orgnummer = "123456789",
                                        deltakelse = "DELTAR",
                                    ),
                                    VirksomhetResponse(
                                        orgnummer = "987654321",
                                        deltakelse = "DELTAR_IKKE",
                                    ),
                                ),
                            ),
                        ),
                    )
                repository.requests shouldBe listOf(listOf("123456789", "987654321"))
            }
        }

        test("get rejects invalid orgnummer") {
            testApplication {
                application {
                    installTiltakspakkeApi(FakeTiltakspakkeVurderingRepository())
                }

                val response = client.get("/api/v1/tiltakspakker/vurdering/123")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain """"message":"orgnummer must be 9 digits""""
            }
        }

        test("get rejects missing orgnummer path parameter") {
            testApplication {
                application {
                    installTiltakspakkeApi(FakeTiltakspakkeVurderingRepository())
                }

                val response = client.get("/api/v1/tiltakspakker/vurdering")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain """"message":"Path parameter orgnummer is required""""
            }
        }

        test("post rejects empty orgnumre list") {
            testApplication {
                application {
                    installTiltakspakkeApi(FakeTiltakspakkeVurderingRepository())
                }

                val response = client.post("/api/v1/tiltakspakker/vurdering") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"orgnumre":[]}""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain """"message":"orgnumre must contain at least one value""""
            }
        }
    })

private fun Application.installTiltakspakkeApi(repository: TiltakspakkeVurderingRepository) {
    installPlugins()

    routing {
        registerTiltakspakkeVurderingApi(repository)
    }
}

private class FakeTiltakspakkeVurderingRepository(
    private val result: List<TiltakspakkeVurdering> = emptyList(),
) : TiltakspakkeVurderingRepository {
    val requests = mutableListOf<List<String>>()

    override suspend fun hentVurderinger(orgnumre: Collection<String>): List<TiltakspakkeVurdering> {
        requests += orgnumre.toList()
        return result
    }
}
