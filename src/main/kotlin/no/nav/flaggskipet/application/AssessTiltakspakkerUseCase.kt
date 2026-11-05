package no.nav.flaggskipet.application

import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.application.port.NewAssessment
import no.nav.flaggskipet.application.port.TiltakspakkeAssessmentRepository
import no.nav.flaggskipet.domain.assessment.AssessmentReason
import no.nav.flaggskipet.domain.assessment.AssessmentResult
import no.nav.flaggskipet.domain.assessment.Orgnummer
import no.nav.flaggskipet.domain.assessment.Participation
import no.nav.flaggskipet.domain.assessment.Tiltakspakke
import no.nav.flaggskipet.domain.assessment.TiltakspakkeAssessment
import no.nav.flaggskipet.domain.assessment.VirksomhetUnderAssessment
import no.nav.flaggskipet.domain.assessment.groupByTiltakspakke

class AssessTiltakspakkerUseCase(
    private val eregClient: EregClient,
    private val tiltakspakkeAssessmentRepository: TiltakspakkeAssessmentRepository,
    private val tiltakspakker: List<Tiltakspakke>,
) {
    suspend fun execute(orgnumre: List<Orgnummer>): List<TiltakspakkeAssessment> {
        if (tiltakspakker.isEmpty()) return emptyList()

        val assessments = getExisting(orgnumre)
            .associateBy { it.tiltakspakkeId to it.orgnummer }
            .toMutableMap()
        val noekkelinfo = mutableMapOf<Orgnummer, EregNoekkelinfo>()

        // Tiltakspakker are assessed in order, so a tiltakspakke can inherit from one saved earlier in this call.
        for (tiltakspakke in tiltakspakker) {
            val missing = orgnumre.distinct().filter { (tiltakspakke.id to it) !in assessments }
            val inheritedFrom = missing.associateWith { assessments[tiltakspakke.inheritsFrom to it] }

            val needsEreg = missing.filter { inheritedFrom[it] == null && it !in noekkelinfo }
            if (needsEreg.isNotEmpty()) {
                noekkelinfo += eregClient.getNoekkelinfo(needsEreg).associateBy { it.orgnummer }
            }

            val newAssessments = missing.mapNotNull { orgnummer ->
                inheritedFrom[orgnummer]?.let { inherit(tiltakspakke, it) }
                    ?: noekkelinfo[orgnummer]?.let { assess(tiltakspakke, it) }
            }
            if (newAssessments.isNotEmpty()) {
                // Saved rows are authoritative, also when a concurrent request saved first.
                tiltakspakkeAssessmentRepository.saveAssessments(newAssessments)
                    .forEach { assessments[it.tiltakspakkeId to it.orgnummer] = it }
            }
        }

        val visibleIds = tiltakspakker.filter { it.visible }.map { it.id }.toSet()
        return assessments.values
            .filter { it.tiltakspakkeId in visibleIds }
            .groupByTiltakspakke()
    }

    private suspend fun getExisting(orgnumre: List<Orgnummer>) = tiltakspakkeAssessmentRepository.getAssessments(
        orgnumre = orgnumre,
        tiltakspakkeIds = (tiltakspakker.map { it.id } + tiltakspakker.mapNotNull { it.inheritsFrom }).distinct(),
    )
}

private fun inherit(
    tiltakspakke: Tiltakspakke,
    source: AssessmentResult,
) = NewAssessment(
    tiltakspakkeId = tiltakspakke.id,
    orgnummer = source.orgnummer,
    participation = source.participation,
    fylkeskode = source.fylkeskode,
    assessmentReason = source.assessmentReason,
)

private fun assess(
    tiltakspakke: Tiltakspakke,
    info: EregNoekkelinfo,
): NewAssessment {
    val adresse = info.adresse
        ?: return assessmentWithoutFylke(tiltakspakke, info, AssessmentReason.MANGLER_ADRESSE)

    val fylkeskode = adresse.fylke()
        ?: return assessmentWithoutFylke(tiltakspakke, info, AssessmentReason.UGYLDIG_KOMMUNENUMMER)
    val participation = tiltakspakke.assess(VirksomhetUnderAssessment(info.orgnummer, adresse))
    val assessmentReason = when {
        fylkeskode == "54" -> AssessmentReason.UTGATT_FYLKESKODE
        participation == Participation.UTENFOR_SCOPE -> AssessmentReason.FYLKE_UTENFOR_SCOPE
        else -> AssessmentReason.FYLKE_I_SCOPE
    }

    return NewAssessment(
        tiltakspakkeId = tiltakspakke.id,
        orgnummer = info.orgnummer,
        participation = participation,
        fylkeskode = fylkeskode,
        assessmentReason = assessmentReason,
    )
}

private fun assessmentWithoutFylke(
    tiltakspakke: Tiltakspakke,
    info: EregNoekkelinfo,
    assessmentReason: AssessmentReason,
) = NewAssessment(
    tiltakspakkeId = tiltakspakke.id,
    orgnummer = info.orgnummer,
    participation = Participation.UTENFOR_SCOPE,
    fylkeskode = null,
    assessmentReason = assessmentReason,
)
