
package no.nav.flaggskipet.domain.vurdering

import kotlinx.datetime.LocalDate
import no.nav.flaggskipet.domain.dagensDato
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

typealias Orgnummer = String

private val KOMMUNENUMMER_FORMAT = Regex("""\d{4}""")

data class Adresse(
    val type: String,
    val postnummer: String,
    val kommunenummer: String,
) {
    fun fylke(): String? = kommunenummer
        .takeIf { it.matches(KOMMUNENUMMER_FORMAT) }
        ?.take(2)
}

data class Tiltakspakke(
    val id: String,
    val sluttdato: LocalDate? = null,
    val regel: Regel,
) {
    fun erGjeldene(now: LocalDate = dagensDato()) = sluttdato?.let { it >= now } ?: true
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

private const val ANTALL_HASHBYTE_SOM_BRUKES = 6
private const val ANTALL_MULIGE_RANDOMISERINGSVERDIER = 1L shl 48

/**
 * Gjør et stabilt, pseudotilfeldig trekk for én nøkkel.
 *
 * SHA-256 gir god spredning selv om orgnumre ligner hverandre. De første 48
 * bitene leses som et positivt heltall og sammenlignes med sannsynligheten
 * ganget med antall mulige 48-bitsverdier. Vi bruker 48 biter fordi alle disse
 * heltallene kan representeres eksakt i en Double. Samme nøkkel og
 * sannsynlighet gir derfor samme resultat på tvers av kall, appinstanser og
 * deployer.
 */
fun trekkesTilTiltaksgruppe(
    sannsynlighet: Double,
    randomiseringsnokkel: String,
): Boolean {
    require(sannsynlighet in 0.0..1.0) {
        "Sannsynlighet må være mellom 0.0 og 1.0"
    }

    val grenseForTiltaksgruppe = sannsynlighet * ANTALL_MULIGE_RANDOMISERINGSVERDIER
    return stabilHashverdi(randomiseringsnokkel) < grenseForTiltaksgruppe
}

private fun stabilHashverdi(randomiseringsnokkel: String): Long {
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(randomiseringsnokkel.toByteArray(StandardCharsets.UTF_8))

    var verdi = 0L
    for (byte in hash.take(ANTALL_HASHBYTE_SOM_BRUKES)) {
        // Flytt de tidligere bitene én byte til venstre og legg den neste byten bakerst.
        verdi = (verdi shl 8) or (byte.toLong() and 0xff)
    }
    return verdi
}
