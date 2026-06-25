package no.nav.flaggskipet.domain.vurdering

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.flaggskipet.infrastructure.clients.ereg.EregClient
import no.nav.flaggskipet.infrastructure.clients.ereg.EregResult
import no.nav.flaggskipet.infrastructure.clients.ereg.Organisasjon
import no.nav.flaggskipet.infrastructure.db.repositories.AdresseVurderingsgrunnlagData
import no.nav.flaggskipet.infrastructure.db.repositories.EregIkkeFunnetVurderingsgrunnlagData
import no.nav.flaggskipet.infrastructure.db.repositories.NyTiltakspakkeVurdering
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurdering
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.infrastructure.db.repositories.VirksomhetDeltakelse
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import kotlin.random.Random
import kotlin.time.Clock

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

class VurderTiltakspakkerUseCase(
    private val eregClient: EregClient,
    private val tiltakspakkeVurderingRepository: TiltakspakkeVurderingRepository,
) {
    suspend fun execute(orgnumre: List<String>): List<TiltakspakkeVurdering> {
        val gjeldeneTiltakspakker = getGjeldendeTiltakspakker().also { logger.debug("Gjeldende tiltakspakker: {}", it) }
        if (gjeldeneTiltakspakker.isEmpty()) return emptyList()
        val eksisterendeVurderinger = tiltakspakkeVurderingRepository.hentVurderinger(
            orgnumre = orgnumre,
            tiltakspakkeIder = gjeldeneTiltakspakker.map { it.tiltakspakke.id },
        )
        val eksisterendeOrgnumre = eksisterendeVurderinger.orgnumre()
        val orgnumreTilVurdering = orgnumre.filterNot { it in eksisterendeOrgnumre }
        if (orgnumreTilVurdering.isEmpty()) return eksisterendeVurderinger

        val adresser = eregClient.hentNoekkelinfo(orgnumreTilVurdering)
        val metadata = VurderingsMetadata(
            tidspunkt = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Oslo")),
            erSann = { sannsynlighet -> Random.Default.nextDouble() < sannsynlighet },
        )

        // Neste skritt:
        // Vurder alle gjendene tiltakene for de manglende bedrifter
        // Opprett nye vurderinger for manglende orgnumre i database
        // merge sammen eksisterende og ny vurderte vurderinger og gir tilbake resultatet.
        val nyeVurderinger = vurder(
            regler = gjeldeneTiltakspakker,
            noekkelinfo = adresser,
            metadata = metadata,
        )
        tiltakspakkeVurderingRepository.lagreVurderinger(nyeVurderinger)

        return mergeVurderinger(eksisterendeVurderinger, nyeVurderinger)
    }
}

private fun List<TiltakspakkeVurdering>.orgnumre(): Set<String> = flatMap { it.virksomheter }
    .map { it.orgnummer }
    .toSet()

internal fun mergeVurderinger(
    eksisterendeVurderinger: List<TiltakspakkeVurdering>,
    nyeVurderinger: List<NyTiltakspakkeVurdering>,
): List<TiltakspakkeVurdering> = (
    eksisterendeVurderinger.flatMap { tiltakspakkeVurdering ->
        tiltakspakkeVurdering.virksomheter.map { virksomhet ->
            tiltakspakkeVurdering.id to virksomhet
        }
    } + nyeVurderinger.map { vurdering ->
        vurdering.tiltakspakkeId to VirksomhetDeltakelse(
            orgnummer = vurdering.orgnummer,
            deltakelse = vurdering.deltakelse,
        )
    }
    ).associateBy(
    keySelector = { (tiltakspakkeId, virksomhet) -> tiltakspakkeId to virksomhet.orgnummer },
    valueTransform = { (tiltakspakkeId, virksomhet) -> tiltakspakkeId to virksomhet },
)
    .values
    .groupBy(
        keySelector = { (tiltakspakkeId, _) -> tiltakspakkeId },
        valueTransform = { (_, virksomhet) -> virksomhet },
    )
    .toSortedMap()
    .map { (tiltakspakkeId, virksomheter) ->
        TiltakspakkeVurdering(
            id = tiltakspakkeId,
            virksomheter = virksomheter.sortedBy(VirksomhetDeltakelse::orgnummer),
        )
    }

private fun vurder(
    regler: List<Regel>,
    noekkelinfo: List<EregResult>,
    metadata: VurderingsMetadata,
): List<NyTiltakspakkeVurdering> = noekkelinfo.flatMap { resultat ->
    when (resultat) {
        is EregResult.Funnet -> vurderFunnetVirksomhet(
            regler = regler,
            resultat = resultat,
            metadata = metadata,
        )

        is EregResult.IkkeFunnet -> regler.map { regel ->
            NyTiltakspakkeVurdering(
                tiltakspakkeId = regel.tiltakspakke.id,
                orgnummer = resultat.organisasjonsnummer,
                deltakelse = Deltakelse.UTENFOR_SCOPE,
                vurderingsgrunnlag = EregIkkeFunnetVurderingsgrunnlagData(resultat.organisasjonsnummer),
            )
        }

        is EregResult.Feil -> emptyList()
    }
}

private fun vurderFunnetVirksomhet(
    regler: List<Regel>,
    resultat: EregResult.Funnet,
    metadata: VurderingsMetadata,
): List<NyTiltakspakkeVurdering> {
    val virksomhet = VirksomhetUnderVurdering(
        orgnummer = resultat.organisasjonsnummer,
        adresse = resultat.organisasjon.adresse,
    )

    return regler.map { regel ->
        NyTiltakspakkeVurdering(
            tiltakspakkeId = regel.tiltakspakke.id,
            orgnummer = virksomhet.orgnummer,
            deltakelse = regel.vurder(
                VurderingsGrunnlag(
                    virksomhet = virksomhet,
                    metadata = metadata,
                ),
            ),
            vurderingsgrunnlag = resultat.organisasjon.adresse.toVurderingsgrunnlagData(),
        )
    }
}

private fun Organisasjon.Adresse.toVurderingsgrunnlagData() = AdresseVurderingsgrunnlagData(
    type = type,
    adresselinje1 = adresselinje1,
    postnummer = postnummer,
    landkode = landkode,
    kommunenummer = kommunenummer,
)
