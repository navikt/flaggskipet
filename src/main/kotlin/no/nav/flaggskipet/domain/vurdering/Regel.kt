
package no.nav.flaggskipet.domain.vurdering

import kotlinx.datetime.LocalDate
import no.nav.flaggskipet.infrastructure.dagensDato
import kotlin.random.Random

typealias Orgnummer = String

data class Adresse(
    val type: String,
    val postnummer: String,
    val kommunenummer: String,
) {
    fun fylke(): String = kommunenummer.take(2)
}

data class Tiltakspakke(
    val id: String,
    val sluttdato: LocalDate? = null,
    val regel: Regel,
) {
    fun erGjeldene(now: LocalDate = dagensDato()) = sluttdato?.let { it > now } ?: true
    fun vurder(virksomhet: VirksomhetUnderVurdering): Deltakelse = regel.vurder(virksomhet)
}

interface Regel {
    fun vurder(virksomhet: VirksomhetUnderVurdering): Deltakelse
}

data class VirksomhetUnderVurdering(
    val orgnummer: Orgnummer,
    val adresse: Adresse,
)

enum class Deltakelse {
    TILTAKSGRUPPE,
    KONTROLLGRUPPE,
    UTENFOR_SCOPE,
}

private val random = Random

fun erSann(sannsynlighet: Double): Boolean {
    require(sannsynlighet in 0.0..1.0) {
        "Sannsynlighet må være mellom 0.0 og 1.0"
    }

    return random.nextDouble() < sannsynlighet
}
