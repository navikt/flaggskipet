package no.nav.flaggskipet.infrastructure.db.repositories

import kotlinx.datetime.LocalDate
import no.nav.flaggskipet.infrastructure.dagensDato
import no.nav.flaggskipet.infrastructure.db.core.transact
import no.nav.flaggskipet.infrastructure.db.tables.TiltakspakkeDeltakelseTable
import no.nav.flaggskipet.infrastructure.db.tables.TiltakspakkeTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
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

enum class Deltakelse {
    DELTAR,
    DELTAR_IKKE,
    UTENFOR_SCOPE,
}

interface TiltakspakkeVurderingRepository {
    suspend fun hentVurderinger(orgnumre: Collection<String>): List<TiltakspakkeVurdering>
}

class TiltakspakkeVurderingRepositoryImpl(
    private val database: Database,
) : TiltakspakkeVurderingRepository {
    override suspend fun hentVurderinger(orgnumre: Collection<String>): List<TiltakspakkeVurdering> {
        return database.transact {
            val dagensDato = LocalDate.parse(dagensDato())

            TiltakspakkeDeltakelseTable
                .innerJoin(TiltakspakkeTable)
                .selectAll()
                .where {
                    (TiltakspakkeDeltakelseTable.orgnummer inList orgnumre) and
                        (
                            TiltakspakkeTable.sluttDato.isNull() or
                                (TiltakspakkeTable.sluttDato greaterEq dagensDato)
                        )
                }.map(ResultRow::toTiltakspakkeDeltakelseRow)
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
    tiltakspakkeId = this[TiltakspakkeTable.id],
    orgnummer = this[TiltakspakkeDeltakelseTable.orgnummer],
    deltakelse = this[TiltakspakkeDeltakelseTable.deltakelse],
)
