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
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock

class TiltakspakkeVurderingRepositoryImpl(
    private val database: Database,
) : TiltakspakkeVurderingRepository {
    override suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<Vurderingsresultat> = database.transact { selectVurderinger(orgnumre, tiltakspakkeIder) }

    override suspend fun lagreVurderinger(
        vurderinger: Collection<VurderingForLagring>,
    ): List<Vurderingsresultat> = database.transact {
        if (vurderinger.isEmpty()) return@transact emptyList()

        val now = Clock.System.now()
        val sorterteVurderinger = vurderinger.sortedWith(
            compareBy(VurderingForLagring::tiltakspakkeId, VurderingForLagring::orgnummer),
        )
        TiltakspakkeDeltakelseTable.batchInsert(
            data = sorterteVurderinger,
            ignore = true,
            shouldReturnGeneratedValues = false,
        ) { vurdering ->
            this[TiltakspakkeDeltakelseTable.tiltakspakkeId] = vurdering.tiltakspakkeId
            this[TiltakspakkeDeltakelseTable.orgnummer] = vurdering.orgnummer
            this[TiltakspakkeDeltakelseTable.deltakelse] = vurdering.deltakelse
            this[TiltakspakkeDeltakelseTable.fylkeskode] = vurdering.fylkeskode
            this[TiltakspakkeDeltakelseTable.vurderingsgrunn] = vurdering.vurderingsgrunn
            this[TiltakspakkeDeltakelseTable.updatedAt] = now
        }

        val vurderingsnokler = vurderinger.map { it.tiltakspakkeId to it.orgnummer }.toSet()
        selectVurderinger(
            orgnumre = vurderinger.map(VurderingForLagring::orgnummer).toSet(),
            tiltakspakkeIder = vurderinger.map(VurderingForLagring::tiltakspakkeId).toSet(),
        ).filter { (it.tiltakspakkeId to it.orgnummer) in vurderingsnokler }
    }

    private fun selectVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<Vurderingsresultat> = TiltakspakkeDeltakelseTable
        .select(
            TiltakspakkeDeltakelseTable.tiltakspakkeId,
            TiltakspakkeDeltakelseTable.deltakelse,
            TiltakspakkeDeltakelseTable.orgnummer,
            TiltakspakkeDeltakelseTable.fylkeskode,
            TiltakspakkeDeltakelseTable.vurderingsgrunn,
        )
        .where { TiltakspakkeDeltakelseTable.orgnummer inList orgnumre }
        .andWhere { TiltakspakkeDeltakelseTable.tiltakspakkeId inList tiltakspakkeIder }
        .map(ResultRow::toVurderingsresultat)
}

private fun ResultRow.toVurderingsresultat() = Vurderingsresultat(
    tiltakspakkeId = this[TiltakspakkeDeltakelseTable.tiltakspakkeId],
    orgnummer = this[TiltakspakkeDeltakelseTable.orgnummer],
    deltakelse = this[TiltakspakkeDeltakelseTable.deltakelse],
    fylkeskode = this[TiltakspakkeDeltakelseTable.fylkeskode],
    vurderingsgrunn = this[TiltakspakkeDeltakelseTable.vurderingsgrunn],
)
