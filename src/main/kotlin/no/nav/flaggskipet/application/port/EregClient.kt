package no.nav.flaggskipet.application.port

import no.nav.flaggskipet.domain.assessment.Adresse

data class EregNoekkelinfo(
    val orgnummer: String,
    val adresse: Adresse?,
)

interface EregClient {
    suspend fun getNoekkelinfo(orgnummer: List<String>): List<EregNoekkelinfo>
}
