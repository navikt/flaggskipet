package no.nav.flaggskipet.api.tiltakspakker

import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.nav.flaggskipet.api.error.ApiErrorException
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurdering
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository
import org.koin.ktor.ext.inject

private val orgnummerPattern = Regex("^\\d{9}$")

fun Application.configureTiltakspakkeVurderingApi() {
    val repository by inject<TiltakspakkeVurderingRepository>()

    routing {
        registerTiltakspakkeVurderingApi(repository)
    }
}

fun Routing.registerTiltakspakkeVurderingApi(repository: TiltakspakkeVurderingRepository) {
    route("/api/v1/tiltakspakker/vurdering") {
        get {
            throw ApiErrorException.BadRequest("Path parameter orgnummer is required")
        }

        get("/{orgnummer}") {
            val orgnummer = requireOrgnummer(call.parameters["orgnummer"])

            call.respond(
                TiltakspakkeVurderingResponse(
                    tiltakspakker = repository.hentVurderinger(listOf(orgnummer)).toResponse(),
                ),
            )
        }

        post {
            val request = call.receive<TiltakspakkeVurderingRequest>()
            val orgnumre = request.validatedOrgnumre()

            call.respond(
                TiltakspakkeVurderingResponse(
                    tiltakspakker = repository.hentVurderinger(orgnumre).toResponse(),
                ),
            )
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

private fun requireOrgnummer(orgnummer: String?): String {
    if (orgnummer == null) {
        throw ApiErrorException.BadRequest("Path parameter orgnummer is required")
    }

    if (!orgnummerPattern.matches(orgnummer)) {
        throw ApiErrorException.BadRequest("orgnummer must be 9 digits")
    }

    return orgnummer
}

private fun TiltakspakkeVurderingRequest.validatedOrgnumre(): List<String> {
    if (orgnumre.isEmpty()) {
        throw ApiErrorException.BadRequest("orgnumre must contain at least one value")
    }

    return orgnumre
        .distinct()
        .onEach { requireOrgnummer(it) }
}

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
