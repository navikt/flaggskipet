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
import no.nav.flaggskipet.domain.assessment.activeTiltakspakker
import no.nav.flaggskipet.domain.assessment.groupByTiltakspakke
import kotlin.collections.plus

class AssessTiltakspakkerUseCase(
    private val eregClient: EregClient,
    private val tiltakspakkeAssessmentRepository: TiltakspakkeAssessmentRepository,
) {
    suspend fun execute(orgnumre: List<Orgnummer>, tiltakspakker: List<Tiltakspakke> = activeTiltakspakker): List<TiltakspakkeAssessment> {
        if (tiltakspakker.isEmpty()) return emptyList()

        val existing = getExisting(tiltakspakker, orgnumre)
        val new = assessAndSave(tiltakspakker, orgnumre, existing)

        return (existing + new).groupByTiltakspakke()
    }

    private suspend fun getExisting(
        tiltakspakker: List<Tiltakspakke>,
        orgnumre: List<Orgnummer>,
    ) = tiltakspakkeAssessmentRepository.getAssessments(
        orgnumre = orgnumre,
        tiltakspakkeIds = tiltakspakker.map { it.id },
    )

    private suspend fun assessAndSave(
        tiltakspakker: List<Tiltakspakke>,
        orgnumre: List<Orgnummer>,
        existing: List<AssessmentResult>,
    ): List<AssessmentResult> {
        val newOrgnumre = orgnumre - existing.map(AssessmentResult::orgnummer).toSet()
        if (newOrgnumre.isEmpty()) return emptyList()

        val assessments = assess(
            tiltakspakker = tiltakspakker,
            noekkelinfo = eregClient.getNoekkelinfo(newOrgnumre),
        )
        return tiltakspakkeAssessmentRepository.saveAssessments(assessments)
    }
}

private fun assess(
    tiltakspakker: List<Tiltakspakke>,
    noekkelinfo: List<EregNoekkelinfo>,
): List<NewAssessment> = noekkelinfo.flatMap { info ->
    tiltakspakker.map { tiltakspakke -> assess(tiltakspakke, info) }
}

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
