package no.nav.flaggskipet.infrastructure.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object InvalidSykmeldingHendelseTable : Table("invalid_sykmelding_hendelse") {
    val topic = text("topic")
    val partition = integer("partition")
    val recordOffset = long("record_offset")
    val errorCode = text("error_code")
    val sykmeldingId = text("sykmelding_id").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(topic, partition, recordOffset)
}
