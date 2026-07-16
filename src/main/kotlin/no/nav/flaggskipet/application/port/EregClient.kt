package no.nav.flaggskipet.application.port

import no.nav.flaggskipet.domain.vurdering.Adresse

data class EregNoekkelinfo(
    val organisasjonsnummer: String,
    val adresse: Adresse?,
)

interface EregClient {
    suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregNoekkelinfo>
}
