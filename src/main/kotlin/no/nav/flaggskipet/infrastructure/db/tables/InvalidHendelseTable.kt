package no.nav.flaggskipet.infrastructure.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object InvalidHendelseTable : Table("invalid_hendelse") {
    val topic = text("topic")
    val partition = integer("partition")
    val recordOffset = long("record_offset")
    val errorCode = text("error_code")
    val rawPayload = text("raw_payload").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(topic, partition, recordOffset)
}
