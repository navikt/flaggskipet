package no.nav.flaggskipet.domain.vurdering

import no.nav.flaggskipet.infrastructure.clients.ereg.EregClient
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository

class TiltakspakkeVurderingUseCase(
    val eregClient: EregClient,
    val tiltakspakkeVurderingRepository: TiltakspakkeVurderingRepository,
) {
    suspend fun vurder(orgnumre: List<String>) {
        val gjeldeneTiltakspakker = getGjeldendeTiltakspakker()
        val eksisterendeVurderinger = tiltakspakkeVurderingRepository.hentVurderinger(orgnumre)

    }
}
