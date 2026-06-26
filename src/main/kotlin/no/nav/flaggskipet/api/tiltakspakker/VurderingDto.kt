package no.nav.flaggskipet.api.tiltakspakker

import kotlinx.serialization.Serializable

@Serializable
data class VurderingRequest(
    val orgnumre: List<String>,
)

@Serializable
data class VurderingResponse(
    val id: String,
    val virksomheter: List<VirksomhetResponse>,
)

@Serializable
data class VirksomhetResponse(
    val orgnummer: String,
    val deltakelse: String,
)
