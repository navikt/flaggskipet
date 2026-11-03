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
import no.nav.flaggskipet.api.error.ApiRejectionReason
import no.nav.flaggskipet.application.AssessTiltakspakkerUseCase
import no.nav.flaggskipet.domain.assessment.TiltakspakkeAssessment

// Limit resources for the synchronous endpoint. The Ereg client also limits
// concurrency against Ereg. Larger populations need separate batches or an
// asynchronous job. Consumers are responsible for authorizing user-orgnummer
// relationships; see README.
private const val MAX_ORGNUMRE = 100

// Format only, not validity: Ereg determines whether an orgnummer exists.
// Mod11 is deliberately omitted. This prevents arbitrary strings from reaching
// the Ereg URL and database. The frontend relies on this validation.
private val ORGNUMMER_FORMAT = Regex("""\d{9}""")

fun Application.configureAssessmentApi() {
    val assessUseCase: AssessTiltakspakkerUseCase by dependencies

    routing {
        authenticate(TOKENX_AUTHENTICATION) {
            route("/api/v1/tiltakspakker/vurdering") {
                post {
                    val request = call.receive<AssessmentRequest>()
                    val orgnumre = request.orgnumre.distinct()
                    if (orgnumre.isEmpty()) {
                        throw ApiErrorException.BadRequest(
                            "orgnumre cannot be empty",
                            rejectionReason = ApiRejectionReason.EMPTY_ORGNUMRE,
                        )
                    }
                    if (orgnumre.size > MAX_ORGNUMRE) {
                        throw ApiErrorException.BadRequest(
                            "Maximum $MAX_ORGNUMRE unique orgnumre per request",
                            rejectionReason = ApiRejectionReason.TOO_MANY_ORGNUMRE,
                        )
                    }
                    if (orgnumre.any { !it.matches(ORGNUMMER_FORMAT) }) {
                        throw ApiErrorException.BadRequest(
                            "Invalid orgnummer: each orgnummer must be exactly 9 digits",
                            rejectionReason = ApiRejectionReason.INVALID_ORGNUMMER_FORMAT,
                        )
                    }
                    call.respond(assessUseCase.execute(orgnumre).toResponse())
                }
            }
        }
    }
}

private fun List<TiltakspakkeAssessment>.toResponse(): List<AssessmentResponse> = map { tiltakspakke ->
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
