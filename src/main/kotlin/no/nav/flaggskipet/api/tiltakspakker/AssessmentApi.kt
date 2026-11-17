package no.nav.flaggskipet.api.tiltakspakker

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
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
import no.nav.flaggskipet.application.AssessmentError
import no.nav.flaggskipet.domain.assessment.Orgnummer
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
                    either {
                        val orgnumre = validate(request.orgnumre).bind()
                        assessUseCase.execute(orgnumre).bind().toDto()
                    }.toResponse()
                }
            }
        }
    }
}

private fun validate(orgnumre: List<String>): Either<AssessmentError, List<Orgnummer>> = either {
    val distinct = orgnumre.distinct()
    ensure(distinct.isNotEmpty()) { AssessmentError.EmptyOrgnumre }
    ensure(distinct.size <= MAX_ORGNUMRE) { AssessmentError.TooManyOrgnumre }
    ensure(distinct.all { it.matches(ORGNUMMER_FORMAT) }) { AssessmentError.InvalidOrgnummerFormat }

    distinct
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
