package no.nav.flaggskipet.infrastructure.db.repositories

import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Orgnummer
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat
import no.nav.flaggskipet.infrastructure.db.config.transact
import no.nav.flaggskipet.infrastructure.db.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

data class VurderingForLagring(
    val tiltakspakkeId: String,
    val orgnummer: Orgnummer,
    val deltakelse: Deltakelse,
)

interface TiltakspakkeVurderingRepository {
    suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<Vurderingsresultat>

    suspend fun lagreVurderinger(
        vurderinger: Collection<VurderingForLagring>,
    )
}

class TiltakspakkeVurderingRepositoryImpl(
    private val database: Database,
) : TiltakspakkeVurderingRepository {
    override suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<Vurderingsresultat> = database.transact {
        TiltakspakkeDeltakelseTable
            .select(
                TiltakspakkeDeltakelseTable.tiltakspakkeId,
                TiltakspakkeDeltakelseTable.deltakelse,
                TiltakspakkeDeltakelseTable.orgnummer,
            )
            .where { TiltakspakkeDeltakelseTable.orgnummer inList orgnumre }
            .andWhere { TiltakspakkeDeltakelseTable.tiltakspakkeId inList tiltakspakkeIder }
            .map(ResultRow::toVurderingsresultat)
    }

    override suspend fun lagreVurderinger(
        vurderinger: Collection<VurderingForLagring>,
    ) {
        database.transact {
            val now = Clock.System.now()

            vurderinger.forEach { v ->
                TiltakspakkeDeltakelseTable.upsert(
                    TiltakspakkeDeltakelseTable.tiltakspakkeId,
                    TiltakspakkeDeltakelseTable.orgnummer,
                ) {
                    it[tiltakspakkeId] = v.tiltakspakkeId
                    it[orgnummer] = v.orgnummer
                    it[deltakelse] = v.deltakelse
                    it[updatedAt] = now
                }
            }
        }
    }
}

private fun ResultRow.toVurderingsresultat() = Vurderingsresultat(
    tiltakspakkeId = this[TiltakspakkeDeltakelseTable.tiltakspakkeId],
    orgnummer = this[TiltakspakkeDeltakelseTable.orgnummer],
    deltakelse = this[TiltakspakkeDeltakelseTable.deltakelse],
)
