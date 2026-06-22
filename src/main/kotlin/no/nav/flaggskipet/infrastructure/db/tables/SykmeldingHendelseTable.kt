package no.nav.flaggskipet.infrastructure.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object SykmeldingHendelseTable : Table("sykmelding_hendelse") {
    val sykmeldingId = text("sykmelding_id")
    val fnr = text("fnr")
    val organisasjonsnummer = text("organisasjonsnummer").nullable()
    val eventTimestamp = timestamp("event_timestamp").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(sykmeldingId)
}
