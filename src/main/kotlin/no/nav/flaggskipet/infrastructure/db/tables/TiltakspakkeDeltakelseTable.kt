package no.nav.flaggskipet.infrastructure.db.tables

import no.nav.flaggskipet.domain.vurdering.Deltakelse
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
object TiltakspakkeDeltakelseTable : Table("tiltakspakke_deltakelse") {
    val id = uuid("id")
    val tiltakspakkeId = text("tiltakspakke_id")
    val orgnummer = text("orgnummer")
    val deltakelse = enumerationByName<Deltakelse>("deltakelse", 32)

    @Suppress("unused")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
