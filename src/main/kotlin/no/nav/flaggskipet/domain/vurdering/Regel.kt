package no.nav.flaggskipet.domain.vurdering

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.flaggskipet.infrastructure.clients.ereg.Organisasjon
import kotlin.time.Clock

data class Tiltakspakke(val id: String, val sluttDato: LocalDateTime? = null) {
    fun gyldig() = sluttDato?.let {
        it > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    } ?: true
}

interface Regel {

    val tiltakspakke: Tiltakspakke

    fun vurder(
        grunnlag: VurderingsGrunnlag,
    ): Deltakelse
}

data class VirksomhetUnderVurdering(val orgnummer: String, val adresse: Organisasjon.Adresse)

data class VurderingsGrunnlag(
    val virksomhet: VirksomhetUnderVurdering,
    val metadata: VurderingsMetadata,
)

enum class Deltakelse {
    DELTAR,
    DELTAR_IKKE,
    UTENFOR_SCOPE,
}

data class VurderingsMetadata(
    val tidspunkt: LocalDateTime,
    val erSann: (Double) -> Boolean,
)
