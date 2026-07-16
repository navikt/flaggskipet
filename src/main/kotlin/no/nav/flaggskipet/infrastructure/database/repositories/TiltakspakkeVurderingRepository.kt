package no.nav.flaggskipet.infrastructure.database.repositories

import no.nav.flaggskipet.application.port.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.application.port.VurderingForLagring
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat
import no.nav.flaggskipet.infrastructure.database.config.transact
import no.nav.flaggskipet.infrastructure.database.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

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
