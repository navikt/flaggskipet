package no.nav.flaggskipet.domain.vurdering

import no.nav.flaggskipet.infrastructure.clients.ereg.EregClient
import no.nav.flaggskipet.infrastructure.clients.ereg.EregNoekkelinfo
import no.nav.flaggskipet.infrastructure.database.repositories.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.infrastructure.database.repositories.VurderingForLagring

class VurderTiltakspakkerUseCase(
    private val eregClient: EregClient,
    private val tiltakspakkeVurderingRepository: TiltakspakkeVurderingRepository,
) {
    suspend fun execute(orgnumre: List<Orgnummer>, tiltakspakker: List<Tiltakspakke> = getGjeldendeTiltakspakker): List<TiltakspakkeVurdering> {
        if (tiltakspakker.isEmpty()) return emptyList()

        val eksisterende = hentEksisterende(tiltakspakker, orgnumre)
        val nye = vurderOgLagre(tiltakspakker, orgnumre, eksisterende)

        return (eksisterende + nye).groupByTiltakspakke()
    }

    private suspend fun hentEksisterende(
        tiltakspakker: List<Tiltakspakke>,
        orgnumre: List<Orgnummer>,
    ) = tiltakspakkeVurderingRepository.hentVurderinger(
        orgnumre = orgnumre,
        tiltakspakkeIder = tiltakspakker.map { it.id },
    )

    private suspend fun vurderOgLagre(
        tiltakspakker: List<Tiltakspakke>,
        orgnumre: List<Orgnummer>,
        eksisterende: List<Vurderingsresultat>,
    ): List<Vurderingsresultat> {
        val nyeOrgnumre = orgnumre - eksisterende.map { it.orgnummer }.toSet()
        if (nyeOrgnumre.isEmpty()) return emptyList()

        val vurderinger = vurder(tiltakspakker, eregClient.hentNoekkelinfo(nyeOrgnumre))
        tiltakspakkeVurderingRepository.lagreVurderinger(vurderinger)
        return vurderinger.map { Vurderingsresultat(it.tiltakspakkeId, it.orgnummer, it.deltakelse) }
    }
}

private fun vurder(
    tiltakspakker: List<Tiltakspakke>,
    noekkelinfo: List<EregNoekkelinfo>,
): List<VurderingForLagring> = noekkelinfo.flatMap { info ->
    tiltakspakker.map { tiltakspakke ->
        VurderingForLagring(
            tiltakspakkeId = tiltakspakke.id,
            orgnummer = info.organisasjonsnummer,
            deltakelse = info.adresse?.let { tiltakspakke.vurder(VirksomhetUnderVurdering(info.organisasjonsnummer, it)) }
                ?: Deltakelse.UTENFOR_SCOPE,
        )
    }
}
