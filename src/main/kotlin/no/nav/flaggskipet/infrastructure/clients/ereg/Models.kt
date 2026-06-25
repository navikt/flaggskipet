package no.nav.flaggskipet.infrastructure.clients.ereg

interface EregClient {
    suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregResult>
}

sealed interface EregResult {
    data class Funnet(
        val organisasjonsnummer: String,
        val organisasjon: Organisasjon,
    ) : EregResult

    data class IkkeFunnet(
        val organisasjonsnummer: String,
    ) : EregResult
}

data class Organisasjon(
    val adresse: Adresse,
) {
    data class Adresse(
        val type: String,
        val postnummer: String,
        val kommunenummer: String,
    ) {
        fun fylke(): String = kommunenummer.take(2)
    }
}
