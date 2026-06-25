package no.nav.flaggskipet.domain.vurdering

import no.nav.flaggskipet.infrastructure.clients.ereg.EregClient
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurdering
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository

class TiltakspakkeVurderingUseCase(
    val eregClient: EregClient,
    val tiltakspakkeVurderingRepository: TiltakspakkeVurderingRepository,
) {
    suspend fun vurder(orgnumre: List<String>): List<TiltakspakkeVurdering> {
        val gjeldeneTiltakspakker = getGjeldendeTiltakspakker()
        if (gjeldeneTiltakspakker.isEmpty()) return emptyList()
        val eksisterendeVurderinger = tiltakspakkeVurderingRepository.hentVurderinger(
            orgnumre = orgnumre,
            tiltakspakkeIder = gjeldeneTiltakspakker.map { it.tiltakspakke.id },
        )
        val eksisterendeOrgnumre = eksisterendeVurderinger.orgnumre()
        val orgnumreTilVurdering = orgnumre.filterNot { it in eksisterendeOrgnumre }
        val adresser = eregClient.hentNoekkelinfo(orgnumreTilVurdering)

        // Neste skritt:

        // Henter bedriftadresser for de orgnumre som mangler
        // Opprett nye vurderinger for manglende orgnumre
        // merge sammen eksisterende og ny vurderte vurderinger
        // gir tilbake resultatet.
        return eksisterendeVurderinger
    }

    private fun hentEksisterendeOrgnumre(
        eksisterendeVurderinger: List<TiltakspakkeVurdering>,
    ): Set<String> = eksisterendeVurderinger
        .flatMap { it.virksomheter }
        .map { it.orgnummer }
        .toSet()
}

private fun List<TiltakspakkeVurdering>.orgnumre(): Set<String> =
    flatMap { it.virksomheter }
        .map { it.orgnummer }
        .toSet()
