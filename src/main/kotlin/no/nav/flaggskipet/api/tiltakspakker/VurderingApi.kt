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

// Ressursbegrensning: konsumentene sender ett orgnummer per kall, og grensen holder
// kallene mot Ereg og databasen små. Autorisasjon av bruker-orgnummer-tilknytning er
// bevisst konsumentens ansvar, se README.
private const val MAKS_ANTALL_ORGNUMRE = 20

// Formatkrav, ikke gyldighetskontroll: Ereg er fasit på om orgnummeret finnes,
// så mod11-sjekk er bevisst utelatt. Kravet hindrer at vilkårlige strenger går
// videre inn i Ereg-URL-en og databasen. Frontend stoler på denne valideringen.
private val ORGNUMMER_FORMAT = Regex("""\d{9}""")

fun Application.configureVurderingApi() {
    val vurderUseCase: VurderTiltakspakkerUseCase by dependencies

    routing {
        authenticate(TOKENX_AUTHENTICATION) {
            route("/api/v1/tiltakspakker/vurdering") {
                post {
                    val request = call.receive<VurderingRequest>()
                    val orgnumre = request.orgnumre.distinct()
                    if (orgnumre.isEmpty()) {
                        throw ApiErrorException.BadRequest("orgnumre kan ikke være tom")
                    }
                    if (orgnumre.size > MAKS_ANTALL_ORGNUMRE) {
                        throw ApiErrorException.BadRequest("Maks $MAKS_ANTALL_ORGNUMRE unike orgnumre per kall")
                    }
                    if (orgnumre.any { !it.matches(ORGNUMMER_FORMAT) }) {
                        throw ApiErrorException.BadRequest("Ugyldig orgnummer: hvert orgnummer må være nøyaktig 9 sifre")
                    }
                    call.respond(vurderUseCase.execute(orgnumre).toResponse())
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
