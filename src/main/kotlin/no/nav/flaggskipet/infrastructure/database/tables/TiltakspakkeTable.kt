package no.nav.flaggskipet.infrastructure.database.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

/** Read-only metadata for analysis, seeded by Flyway migrations. */
object TiltakspakkeTable : Table("tiltakspakke") {
    val id = text("id")
    val startDate = date("startdato").nullable()
    val endDate = date("sluttdato").nullable()
    val inheritsFrom = text("arver_fra").nullable()

    override val primaryKey = PrimaryKey(id)
}
