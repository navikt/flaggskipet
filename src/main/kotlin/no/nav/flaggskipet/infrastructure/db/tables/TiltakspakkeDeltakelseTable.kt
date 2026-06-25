package no.nav.flaggskipet.infrastructure.db.tables

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.postgresql.util.PGobject

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
object TiltakspakkeDeltakelseTable : Table("tiltakspakke_deltakelse") {
    val id = uuid("id")
    val tiltakspakkeId = text("tiltakspakke_id")
    val orgnummer = text("orgnummer")
    val deltakelse = enumerationByName<Deltakelse>("deltakelse", 32)
    val vurderingsgrunnlag = jsonb("vurderingsgrunnlag")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

private val json = Json.Default

private fun Table.jsonb(name: String): Column<JsonObject> = registerColumn(name, JsonbColumnType())

private class JsonbColumnType : ColumnType<JsonObject>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): JsonObject = when (value) {
        is PGobject -> json.parseToJsonElement(value.value ?: error("JSONB value was null")).jsonObject
        is String -> json.parseToJsonElement(value).jsonObject
        else -> error("Unexpected JSONB value of type ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: JsonObject): Any = PGobject().apply {
        type = "jsonb"
        this.value = json.encodeToString(JsonObject.serializer(), value)
    }
}
