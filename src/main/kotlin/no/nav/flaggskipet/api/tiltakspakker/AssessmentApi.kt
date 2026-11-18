package no.nav.flaggskipet.api.tiltakspakker

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receive
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.flaggskipet.api.auth.TOKENX_AUTHENTICATION
import no.nav.flaggskipet.api.error.toResponse
import no.nav.flaggskipet.application.AssessTiltakspakkerUseCase
import no.nav.flaggskipet.domain.assessment.TiltakspakkeAssessment

fun Application.configureAssessmentApi() {
    val assessUseCase: AssessTiltakspakkerUseCase by dependencies

    routing {
        authenticate(TOKENX_AUTHENTICATION) {
            route("/api/v1/tiltakspakker/vurdering") {
                post {
                    val request = call.receive<AssessmentRequest>()
                    assessUseCase.execute(request.orgnumre).map { it.toDto() }.toResponse()
                }
            }
        }
    }
}

private fun List<TiltakspakkeAssessment>.toDto(): List<AssessmentResponse> = map { tiltakspakke ->
    AssessmentResponse(
        tiltakspakkeId = tiltakspakke.id,
        virksomheter = tiltakspakke.virksomheter.map { virksomhet ->
            VirksomhetResponse(
                orgnummer = virksomhet.orgnummer,
                deltakelse = virksomhet.participation.name,
            )
        },
    )
}
