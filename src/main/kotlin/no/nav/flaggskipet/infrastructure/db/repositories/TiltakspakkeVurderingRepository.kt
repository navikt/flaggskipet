package no.nav.flaggskipet.infrastructure.db.repositories

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.infrastructure.db.core.transact
import no.nav.flaggskipet.infrastructure.db.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

data class TiltakspakkeVurdering(
    val id: String,
    val virksomheter: List<VirksomhetDeltakelse>,
)

data class VirksomhetDeltakelse(
    val orgnummer: String,
    val deltakelse: Deltakelse,
)

data class NyTiltakspakkeVurdering(
    val tiltakspakkeId: String,
    val orgnummer: String,
    val deltakelse: Deltakelse,
    val vurderingsgrunnlag: VurderingsgrunnlagData,
)

sealed interface VurderingsgrunnlagData {
    fun toJsonObject(): JsonObject
}

data class AdresseVurderingsgrunnlagData(
    val type: String,
    val postnummer: String,
    val kommunenummer: String,
) : VurderingsgrunnlagData {
    override fun toJsonObject(): JsonObject = buildJsonObject {
        put("type", type)
        put("postnummer", postnummer)
        put("kommunenummer", kommunenummer)
    }
}

data class EregIkkeFunnetVurderingsgrunnlagData(
    val organisasjonsnummer: String,
) : VurderingsgrunnlagData {
    override fun toJsonObject(): JsonObject = buildJsonObject {
        put("type", "EREG_IKKE_FUNNET")
        put("organisasjonsnummer", organisasjonsnummer)
    }
}

interface TiltakspakkeVurderingRepository {
    suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<TiltakspakkeVurdering>

    suspend fun lagreVurderinger(
        vurderinger: Collection<NyTiltakspakkeVurdering>,
    )
}

class TiltakspakkeVurderingRepositoryImpl(
    private val database: Database,
) : TiltakspakkeVurderingRepository {
    override suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<TiltakspakkeVurdering> = database.transact {
        TiltakspakkeDeltakelseTable
            .select(
                TiltakspakkeDeltakelseTable.tiltakspakkeId,
                TiltakspakkeDeltakelseTable.deltakelse,
                TiltakspakkeDeltakelseTable.orgnummer,
            )
            .where { TiltakspakkeDeltakelseTable.orgnummer inList orgnumre }
            .andWhere { TiltakspakkeDeltakelseTable.tiltakspakkeId inList tiltakspakkeIder }
            .map(ResultRow::toTiltakspakkeDeltakelseRow)
            .groupBy(TiltakspakkeDeltakelseRow::tiltakspakkeId)
            .map { (tiltakspakkeId, rows) ->
                TiltakspakkeVurdering(
                    id = tiltakspakkeId,
                    virksomheter = rows
                        .map { row ->
                            VirksomhetDeltakelse(
                                orgnummer = row.orgnummer,
                                deltakelse = row.deltakelse,
                            )
                        },
                )
            }
    }

    override suspend fun lagreVurderinger(
        vurderinger: Collection<NyTiltakspakkeVurdering>,
    ) {
        database.transact {
            val now = Clock.System.now()

            vurderinger.forEach { vurdering ->
                TiltakspakkeDeltakelseTable.upsert(
                    TiltakspakkeDeltakelseTable.tiltakspakkeId,
                    TiltakspakkeDeltakelseTable.orgnummer,
                ) {
                    it[tiltakspakkeId] = vurdering.tiltakspakkeId
                    it[orgnummer] = vurdering.orgnummer
                    it[deltakelse] = vurdering.deltakelse
                    it[vurderingsgrunnlag] = vurdering.vurderingsgrunnlag.toJsonObject()
                    it[updatedAt] = now
                }
            }
        }
    }
}

private data class TiltakspakkeDeltakelseRow(
    val tiltakspakkeId: String,
    val orgnummer: String,
    val deltakelse: Deltakelse,
)

private fun ResultRow.toTiltakspakkeDeltakelseRow() = TiltakspakkeDeltakelseRow(
    tiltakspakkeId = this[TiltakspakkeDeltakelseTable.tiltakspakkeId],
    orgnummer = this[TiltakspakkeDeltakelseTable.orgnummer],
    deltakelse = this[TiltakspakkeDeltakelseTable.deltakelse],
)
