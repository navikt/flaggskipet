package no.nav.flaggskipet.domain.vurdering

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import no.nav.flaggskipet.infrastructure.clients.ereg.Organisasjon
import no.nav.flaggskipet.infrastructure.dagensDato
import java.security.SecureRandom

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

private val random = SecureRandom()

fun erSann(sannsynlighet: Double): Boolean {
    require(sannsynlighet in 0.0..1.0) {
        "Sannsynlighet må være mellom 0.0 og 1.0"
    }

    return random.nextDouble() < sannsynlighet
}

data class VurderingsMetadata(
    val tidspunkt: LocalDateTime,
    val erSann: (Double) -> Boolean = ::erSann,
)
