package no.nav.flaggskipet.domain.assessment

import kotlinx.datetime.LocalDate
import no.nav.flaggskipet.domain.today
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
    val endDate: LocalDate? = null,
    val rule: Rule,
    val inheritsFrom: String? = null,
    val visible: Boolean = true,
) {
    fun isActive(now: LocalDate = today()) = endDate?.let { it >= now } ?: true
    fun assess(virksomhet: VirksomhetUnderAssessment): Participation = rule.assess(virksomhet)
}

interface Rule {
    fun assess(virksomhet: VirksomhetUnderAssessment): Participation
}

data class VirksomhetUnderAssessment(
    val orgnummer: Orgnummer,
    val adresse: Adresse,
)

enum class Participation {
    TILTAKSGRUPPE,
    KONTROLLGRUPPE,
    UTENFOR_SCOPE,
}

private const val HASH_BYTES_USED = 6
private const val POSSIBLE_RANDOMIZATION_VALUES = 1L shl 48

/**
 * Assigns an orgnummer to Tiltaksgruppe in a stable, pseudorandom way for one key.
 *
 * SHA-256 spreads values even when orgnumre are similar. The first 48 bits
 * form a positive integer compared against the desired Tiltaksgruppe share
 * times the number of possible 48-bit values. Every such integer is exactly
 * representable as a Double. The same key and share yield the same result
 * across calls, application instances and deployments.
 */
fun isAssignedToTiltaksgruppe(
    tiltaksgruppeShare: Double,
    randomizationKey: String,
): Boolean {
    require(tiltaksgruppeShare in 0.0..1.0) {
        "Tiltaksgruppe share must be between 0.0 and 1.0"
    }

    val tiltaksgruppeThreshold = tiltaksgruppeShare * POSSIBLE_RANDOMIZATION_VALUES
    return stableHashValue(randomizationKey) < tiltaksgruppeThreshold
}

private fun stableHashValue(randomizationKey: String): Long {
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(randomizationKey.toByteArray(StandardCharsets.UTF_8))

    var value = 0L
    for (byte in hash.take(HASH_BYTES_USED)) {
        // Shift the previous bits left by one byte and append the next byte.
        value = (value shl 8) or (byte.toLong() and 0xff)
    }
    return value
}
