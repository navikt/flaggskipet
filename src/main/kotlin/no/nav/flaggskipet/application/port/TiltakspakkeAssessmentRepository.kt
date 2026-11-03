package no.nav.flaggskipet.application.port

import no.nav.flaggskipet.domain.assessment.AssessmentReason
import no.nav.flaggskipet.domain.assessment.AssessmentResult
import no.nav.flaggskipet.domain.assessment.Orgnummer
import no.nav.flaggskipet.domain.assessment.Participation

data class NewAssessment(
    val tiltakspakkeId: String,
    val orgnummer: Orgnummer,
    val participation: Participation,
    val fylkeskode: String?,
    val assessmentReason: AssessmentReason,
)

interface TiltakspakkeAssessmentRepository {
    suspend fun getAssessments(
        orgnumre: Collection<String>,
        tiltakspakkeIds: Collection<String>,
    ): List<AssessmentResult>

    suspend fun saveAssessments(
        assessments: Collection<NewAssessment>,
    ): List<AssessmentResult>
}
