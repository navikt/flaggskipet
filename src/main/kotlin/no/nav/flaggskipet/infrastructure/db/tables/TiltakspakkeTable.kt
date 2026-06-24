package no.nav.flaggskipet.infrastructure.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

object TiltakspakkeTable : Table("tiltakspakke") {
    val id = text("id")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    val sluttDato = date("slutt_dato").nullable()

    override val primaryKey = PrimaryKey(id)
}
