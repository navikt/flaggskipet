package no.nav.flaggskipet.api.tiltakspakker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.flaggskipet.api.error.ApiErrorException
import no.nav.flaggskipet.domain.vurdering.TiltakspakkeVurdering
import no.nav.flaggskipet.domain.vurdering.VurderTiltakspakkerUseCase
import org.koin.ktor.ext.inject

fun Application.configureVurderingApi() {
    val vurderUseCase by inject<VurderTiltakspakkerUseCase>()

    routing {
        route("/api/v1/tiltakspakker/vurdering") {
            get {
                throw ApiErrorException.BadRequest("Path parameter orgnummer is required")
            }

            get("/{orgnummer}") {
                val orgnummer = call.parameters["orgnummer"]
                if (orgnummer.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NoContent)
                    return@get
                }
                call.respond(vurderUseCase.execute(listOf(orgnummer)).toResponse())
            }

            post {
                val request = call.receive<VurderingRequest>()
                vurderUseCase.execute(request.orgnumre).toResponse()
                call.respond(vurderUseCase.execute(request.orgnumre).toResponse())
            }
        }
    }
}

private fun List<TiltakspakkeVurdering>.toResponse(): List<VurderingResponse> = map { tiltakspakke ->
    VurderingResponse(
        id = tiltakspakke.id,
        virksomheter = tiltakspakke.virksomheter.map { virksomhet ->
            VirksomhetResponse(
                orgnummer = virksomhet.orgnummer,
                deltakelse = virksomhet.deltakelse.name,
            )
        },
    )
}
