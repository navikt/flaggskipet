package no.nav.flaggskipet.domain.assessment

data class AssessmentResult(
    val tiltakspakkeId: String,
    val orgnummer: Orgnummer,
    val participation: Participation,
    val fylkeskode: String? = null,
    val assessmentReason: AssessmentReason = AssessmentReason.IKKE_REGISTRERT,
)

data class TiltakspakkeAssessment(
    val id: String,
    val virksomheter: List<VirksomhetParticipation>,
)

data class VirksomhetParticipation(
    val orgnummer: Orgnummer,
    val participation: Participation,
)

fun List<AssessmentResult>.groupByTiltakspakke(): List<TiltakspakkeAssessment> = groupBy { it.tiltakspakkeId }
    .toSortedMap()
    .map { (tiltakspakkeId, results) ->
        TiltakspakkeAssessment(
            id = tiltakspakkeId,
            virksomheter = results.map { VirksomhetParticipation(it.orgnummer, it.participation) }
                .sortedBy { it.orgnummer },
        )
    }
