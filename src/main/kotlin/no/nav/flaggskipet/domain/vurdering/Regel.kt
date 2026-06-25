package no.nav.flaggskipet.domain.vurdering

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import no.nav.flaggskipet.infrastructure.clients.ereg.Organisasjon
import no.nav.flaggskipet.infrastructure.dagensDato

data class Tiltakspakke(val id: String, val sluttdato: LocalDate? = null) {
    fun erGjeldene(now: LocalDate = dagensDato()) = sluttdato?.compareTo(now)?.let { it > 0 } ?: true
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
