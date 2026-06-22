package no.nav.flaggskipet.infrastructure.db.repositories

import no.nav.flaggskipet.infrastructure.db.core.transact
import no.nav.flaggskipet.infrastructure.db.tables.SykmeldingHendelseTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.time.Instant

data class SykmeldingHendelse(
    val sykmeldingId: String,
    val fnr: String,
    val organisasjonsnummer: String?,
    val eventTimestamp: Instant?,
)

interface SykmeldingHendelseRepository {
    suspend fun upsert(hendelse: SykmeldingHendelse)
}

class SykmeldingHendelseRepositoryImpl(
    private val database: Database,
) : SykmeldingHendelseRepository {
    override suspend fun upsert(hendelse: SykmeldingHendelse) {
        database.transact {
            SykmeldingHendelseTable.upsert(SykmeldingHendelseTable.sykmeldingId) {
                it[sykmeldingId] = hendelse.sykmeldingId
                it[fnr] = hendelse.fnr
                it[organisasjonsnummer] = hendelse.organisasjonsnummer
                it[eventTimestamp] = hendelse.eventTimestamp
                it[updatedAt] = Clock.System.now()
            }
        }
    }
}
