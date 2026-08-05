package no.nav.flaggskipet.api.tiltakspakker

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.flaggskipet.api.auth.TOKENX_AUTHENTICATION
import no.nav.flaggskipet.api.error.ApiErrorException
import no.nav.flaggskipet.application.VurderTiltakspakkerUseCase
import no.nav.flaggskipet.domain.vurdering.TiltakspakkeVurdering

// Flaggskipet validerer ikke at innlogget bruker representerer orgnumrene i requesten.
// Grensen gjør masseoppslag av tiltaksgruppen upraktisk uten å merkes av konsumentene,
// som sender ett orgnummer per kall.
private const val MAKS_ANTALL_ORGNUMRE = 20

fun Application.configureVurderingApi() {
    val vurderUseCase: VurderTiltakspakkerUseCase by dependencies

    routing {
        authenticate(TOKENX_AUTHENTICATION) {
            route("/api/v1/tiltakspakker/vurdering") {
                post {
                    val request = call.receive<VurderingRequest>()
                    if (request.orgnumre.size > MAKS_ANTALL_ORGNUMRE) {
                        throw ApiErrorException.BadRequest("Maks $MAKS_ANTALL_ORGNUMRE orgnumre per kall")
                    }
                    call.respond(vurderUseCase.execute(request.orgnumre).toResponse())
                }
            }
        }
    }
}

private fun List<TiltakspakkeVurdering>.toResponse(): List<VurderingResponse> = map { tiltakspakke ->
    VurderingResponse(
        tiltakspakkeId = tiltakspakke.id,
        virksomheter = tiltakspakke.virksomheter.map { virksomhet ->
            VirksomhetResponse(
                orgnummer = virksomhet.orgnummer,
                deltakelse = virksomhet.deltakelse.name,
            )
        },
    )
}
