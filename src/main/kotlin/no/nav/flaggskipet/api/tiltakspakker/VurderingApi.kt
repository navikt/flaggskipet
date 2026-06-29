package no.nav.flaggskipet.api.tiltakspakker

import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.flaggskipet.domain.vurdering.TiltakspakkeVurdering
import no.nav.flaggskipet.domain.vurdering.VurderTiltakspakkerUseCase
import org.koin.ktor.ext.inject

fun Application.configureVurderingApi() {
    val vurderUseCase by inject<VurderTiltakspakkerUseCase>()

    routing {
        route("/api/v1/tiltakspakker/vurdering") {
            post {
                val request = call.receive<VurderingRequest>()
                call.respond(vurderUseCase.execute(request.orgnumre).toResponse())
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
