package no.nav.flaggskipet.domain.vurdering

data class Vurderingsresultat(
    val tiltakspakkeId: String,
    val orgnummer: Orgnummer,
    val deltakelse: Deltakelse,
)

data class TiltakspakkeVurdering(
    val id: String,
    val virksomheter: List<VirksomhetDeltakelse>,
)

data class VirksomhetDeltakelse(
    val orgnummer: Orgnummer,
    val deltakelse: Deltakelse,
)

fun List<Vurderingsresultat>.groupByTiltakspakke(): List<TiltakspakkeVurdering> = groupBy { it.tiltakspakkeId }
    .toSortedMap()
    .map { (tiltakspakkeId, resultater) ->
        TiltakspakkeVurdering(
            id = tiltakspakkeId,
            virksomheter = resultater.map { VirksomhetDeltakelse(it.orgnummer, it.deltakelse) }
                .sortedBy { it.orgnummer },
        )
    }
