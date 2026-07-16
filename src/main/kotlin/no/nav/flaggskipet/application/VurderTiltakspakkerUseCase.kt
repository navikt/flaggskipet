package no.nav.flaggskipet.application

import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.application.port.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.application.port.VurderingForLagring
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Orgnummer
import no.nav.flaggskipet.domain.vurdering.Tiltakspakke
import no.nav.flaggskipet.domain.vurdering.TiltakspakkeVurdering
import no.nav.flaggskipet.domain.vurdering.VirksomhetUnderVurdering
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat
import no.nav.flaggskipet.domain.vurdering.getGjeldendeTiltakspakker
import no.nav.flaggskipet.domain.vurdering.groupByTiltakspakke
import kotlin.collections.plus

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
