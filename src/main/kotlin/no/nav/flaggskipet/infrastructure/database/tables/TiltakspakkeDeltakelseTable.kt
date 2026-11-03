package no.nav.flaggskipet.infrastructure.database.tables

import no.nav.flaggskipet.domain.assessment.AssessmentReason
import no.nav.flaggskipet.domain.assessment.Participation
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.regexp
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object TiltakspakkeDeltakelseTable : Table("tiltakspakke_deltakelse") {
    val id = javaUUID("id").databaseGenerated()
    val tiltakspakkeId = text("tiltakspakke_id")
    val orgnummer = text("orgnummer")
    val participation = enumerationByName<Participation>("deltakelse", 32)
    val fylkeskode = varchar("fylkeskode", 2).nullable()

    val assessmentReason = text("vurderingsgrunn")
        .transform(AssessmentReason::valueOf, AssessmentReason::name)

    @Suppress("unused")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        check("chk_tiltakspakke_deltakelse") { participation inList Participation.entries.toList() }
        check("chk_tiltakspakke_fylkeskode") { (fylkeskode eq null) or (fylkeskode regexp "^[0-9]{2}$") }
        index("uq_tiltakspakke_orgnr", true, tiltakspakkeId, orgnummer)
    }
}
