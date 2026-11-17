package no.nav.flaggskipet.application.port

import arrow.core.Either
import no.nav.flaggskipet.domain.assessment.Adresse

data class EregNoekkelinfo(
    val orgnummer: String,
    val adresse: Adresse?,
)

sealed interface EregError {
    data class UnexpectedStatus(val status: Int) : EregError
    data object Timeout : EregError
}

interface EregClient {
    suspend fun getNoekkelinfo(orgnummer: List<String>): Either<EregError, List<EregNoekkelinfo>>
}
