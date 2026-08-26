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
import no.nav.flaggskipet.domain.vurdering.Vurderingsgrunn
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
        val nyeOrgnumre = orgnumre - eksisterende.map(Vurderingsresultat::orgnummer).toSet()
        if (nyeOrgnumre.isEmpty()) return emptyList()

        val vurderinger = vurder(
            tiltakspakker = tiltakspakker,
            noekkelinfo = eregClient.hentNoekkelinfo(nyeOrgnumre),
        )
        return tiltakspakkeVurderingRepository.lagreVurderinger(vurderinger)
    }
}

private fun vurder(
    tiltakspakker: List<Tiltakspakke>,
    noekkelinfo: List<EregNoekkelinfo>,
): List<VurderingForLagring> = noekkelinfo.flatMap { info ->
    tiltakspakker.map { tiltakspakke -> vurder(tiltakspakke, info) }
}

private fun vurder(
    tiltakspakke: Tiltakspakke,
    info: EregNoekkelinfo,
): VurderingForLagring {
    val adresse = info.adresse
        ?: return vurderingUtenFylkesgrunnlag(tiltakspakke, info, Vurderingsgrunn.MANGLER_ADRESSE)

    val fylkeskode = adresse.fylke()
        ?: return vurderingUtenFylkesgrunnlag(tiltakspakke, info, Vurderingsgrunn.UGYLDIG_KOMMUNENUMMER)
    val deltakelse = tiltakspakke.vurder(VirksomhetUnderVurdering(info.organisasjonsnummer, adresse))
    val vurderingsgrunn = when {
        fylkeskode == "54" -> Vurderingsgrunn.UTGATT_FYLKESKODE
        deltakelse == Deltakelse.UTENFOR_SCOPE -> Vurderingsgrunn.FYLKE_UTENFOR_SCOPE
        else -> Vurderingsgrunn.FYLKE_I_SCOPE
    }

    return VurderingForLagring(
        tiltakspakkeId = tiltakspakke.id,
        orgnummer = info.organisasjonsnummer,
        deltakelse = deltakelse,
        fylkeskode = fylkeskode,
        vurderingsgrunn = vurderingsgrunn,
    )
}

private fun vurderingUtenFylkesgrunnlag(
    tiltakspakke: Tiltakspakke,
    info: EregNoekkelinfo,
    vurderingsgrunn: Vurderingsgrunn,
) = VurderingForLagring(
    tiltakspakkeId = tiltakspakke.id,
    orgnummer = info.organisasjonsnummer,
    deltakelse = Deltakelse.UTENFOR_SCOPE,
    fylkeskode = null,
    vurderingsgrunn = vurderingsgrunn,
)
