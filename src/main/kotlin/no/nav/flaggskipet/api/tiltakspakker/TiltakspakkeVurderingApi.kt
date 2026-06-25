package no.nav.flaggskipet.api.tiltakspakker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.nav.flaggskipet.api.error.ApiErrorException
import no.nav.flaggskipet.domain.vurdering.VurderTiltakspakkerUseCase
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurdering
import org.koin.ktor.ext.inject

fun Application.configureTiltakspakkeVurderingApi() {
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
                val request = call.receive<TiltakspakkeVurderingRequest>()
                vurderUseCase.execute(request.orgnumre).toResponse()
                call.respond(vurderUseCase.execute(request.orgnumre).toResponse())
            }
        }
    }
}

@Serializable
data class TiltakspakkeVurderingRequest(
    val orgnumre: List<String>,
)

@Serializable
data class TiltakspakkeVurderingResponse(
    val tiltakspakker: List<TiltakspakkeResponse>,
)

@Serializable
data class TiltakspakkeResponse(
    val id: String,
    val virksomheter: List<VirksomhetResponse>,
)

@Serializable
data class VirksomhetResponse(
    val orgnummer: String,
    val deltakelse: String,
)

private fun List<TiltakspakkeVurdering>.toResponse(): List<TiltakspakkeResponse> = map { tiltakspakke ->
    TiltakspakkeResponse(
        id = tiltakspakke.id,
        virksomheter = tiltakspakke.virksomheter.map { virksomhet ->
            VirksomhetResponse(
                orgnummer = virksomhet.orgnummer,
                deltakelse = virksomhet.deltakelse.name,
            )
        },
    )
}
