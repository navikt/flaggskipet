package no.nav.flaggskipet.application.port

import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Orgnummer
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat

data class VurderingForLagring(
    val tiltakspakkeId: String,
    val orgnummer: Orgnummer,
    val deltakelse: Deltakelse,
)

interface TiltakspakkeVurderingRepository {
    suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<Vurderingsresultat>

    suspend fun lagreVurderinger(
        vurderinger: Collection<VurderingForLagring>,
    )
}
