package no.nav.flaggskipet.infrastructure.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object SykmeldingHendelseTable : Table("sykmelding_hendelse") {
    val sykmeldingId = text("sykmelding_id")
    val fnr = text("fnr")
    val organisasjonsnummer = text("organisasjonsnummer").nullable()
    val periodeFom = date("periode_fom").nullable()
    val periodeTom = date("periode_tom").nullable()
    val eventTimestamp = timestampWithTimeZone("event_timestamp").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(sykmeldingId)
}
