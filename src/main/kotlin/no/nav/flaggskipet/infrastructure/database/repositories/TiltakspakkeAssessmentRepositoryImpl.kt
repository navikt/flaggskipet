package no.nav.flaggskipet.infrastructure.database.repositories

import no.nav.flaggskipet.application.port.NewAssessment
import no.nav.flaggskipet.application.port.TiltakspakkeAssessmentRepository
import no.nav.flaggskipet.domain.assessment.AssessmentResult
import no.nav.flaggskipet.infrastructure.database.config.transact
import no.nav.flaggskipet.infrastructure.database.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock

class TiltakspakkeAssessmentRepositoryImpl(
    private val database: Database,
) : TiltakspakkeAssessmentRepository {
    override suspend fun getAssessments(
        orgnumre: Collection<String>,
        tiltakspakkeIds: Collection<String>,
    ): List<AssessmentResult> = database.transact { selectAssessments(orgnumre, tiltakspakkeIds) }

    override suspend fun saveAssessments(
        assessments: Collection<NewAssessment>,
    ): List<AssessmentResult> = database.transact {
        if (assessments.isEmpty()) return@transact emptyList()

        val now = Clock.System.now()
        val sortedAssessments = assessments.sortedWith(
            compareBy(NewAssessment::tiltakspakkeId, NewAssessment::orgnummer),
        )
        TiltakspakkeDeltakelseTable.batchInsert(
            data = sortedAssessments,
            ignore = true,
            shouldReturnGeneratedValues = false,
        ) { assessment ->
            this[TiltakspakkeDeltakelseTable.tiltakspakkeId] = assessment.tiltakspakkeId
            this[TiltakspakkeDeltakelseTable.orgnummer] = assessment.orgnummer
            this[TiltakspakkeDeltakelseTable.participation] = assessment.participation
            this[TiltakspakkeDeltakelseTable.fylkeskode] = assessment.fylkeskode
            this[TiltakspakkeDeltakelseTable.assessmentReason] = assessment.assessmentReason
            this[TiltakspakkeDeltakelseTable.updatedAt] = now
        }

        val assessmentKeys = assessments.map { it.tiltakspakkeId to it.orgnummer }.toSet()
        selectAssessments(
            orgnumre = assessments.map(NewAssessment::orgnummer).toSet(),
            tiltakspakkeIds = assessments.map(NewAssessment::tiltakspakkeId).toSet(),
        ).filter { (it.tiltakspakkeId to it.orgnummer) in assessmentKeys }
    }

    private fun selectAssessments(
        orgnumre: Collection<String>,
        tiltakspakkeIds: Collection<String>,
    ): List<AssessmentResult> = TiltakspakkeDeltakelseTable
        .select(
            TiltakspakkeDeltakelseTable.tiltakspakkeId,
            TiltakspakkeDeltakelseTable.participation,
            TiltakspakkeDeltakelseTable.orgnummer,
            TiltakspakkeDeltakelseTable.fylkeskode,
            TiltakspakkeDeltakelseTable.assessmentReason,
        )
        .where { TiltakspakkeDeltakelseTable.orgnummer inList orgnumre }
        .andWhere { TiltakspakkeDeltakelseTable.tiltakspakkeId inList tiltakspakkeIds }
        .map(ResultRow::toAssessmentResult)
}

private fun ResultRow.toAssessmentResult() = AssessmentResult(
    tiltakspakkeId = this[TiltakspakkeDeltakelseTable.tiltakspakkeId],
    orgnummer = this[TiltakspakkeDeltakelseTable.orgnummer],
    participation = this[TiltakspakkeDeltakelseTable.participation],
    fylkeskode = this[TiltakspakkeDeltakelseTable.fylkeskode],
    assessmentReason = this[TiltakspakkeDeltakelseTable.assessmentReason],
)
