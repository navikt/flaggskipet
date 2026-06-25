package no.nav.flaggskipet.infrastructure.db.repositories

import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.infrastructure.db.core.transact
import no.nav.flaggskipet.infrastructure.db.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

data class TiltakspakkeVurdering(
    val id: String,
    val virksomheter: List<VirksomhetDeltakelse>,
)

data class VirksomhetDeltakelse(
    val orgnummer: String,
    val deltakelse: Deltakelse,
)

interface TiltakspakkeVurderingRepository {
    suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<TiltakspakkeVurdering>
}

class TiltakspakkeVurderingRepositoryImpl(
    private val database: Database,
) : TiltakspakkeVurderingRepository {
    override suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<TiltakspakkeVurdering> {
        if (orgnumre.isEmpty() || tiltakspakkeIder.isEmpty()) {
            return emptyList()
        }

        return database.transact {
            TiltakspakkeDeltakelseTable
                .selectAll()
                .where { TiltakspakkeDeltakelseTable.orgnummer inList orgnumre }
                .andWhere { TiltakspakkeDeltakelseTable.tiltakspakkeId inList tiltakspakkeIder }
                .map(ResultRow::toTiltakspakkeDeltakelseRow)
                .groupBy(TiltakspakkeDeltakelseRow::tiltakspakkeId)
                .toSortedMap()
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
