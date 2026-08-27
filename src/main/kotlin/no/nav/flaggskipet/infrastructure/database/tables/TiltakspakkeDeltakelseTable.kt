package no.nav.flaggskipet.infrastructure.database.tables

import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Vurderingsgrunn
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
    val deltakelse = enumerationByName<Deltakelse>("deltakelse", 32)
    val fylkeskode = varchar("fylkeskode", 2).nullable()

    val vurderingsgrunn = text("vurderingsgrunn")
        .transform(Vurderingsgrunn::valueOf, Vurderingsgrunn::name)

    @Suppress("unused")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        check("chk_tiltakspakke_deltakelse") { deltakelse inList Deltakelse.entries.toList() }
        check("chk_tiltakspakke_fylkeskode") { (fylkeskode eq null) or (fylkeskode regexp "^[0-9]{2}$") }
        index("uq_tiltakspakke_orgnr", true, tiltakspakkeId, orgnummer)
    }
}
