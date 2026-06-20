package no.nav.flaggskipet.infrastructure.db

import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class SykmeldingHendelse(
    val sykmeldingId: String,
    val fnr: String,
    val organisasjonsnummer: String?,
    val periodeFom: LocalDate?,
    val periodeTom: LocalDate?,
    val eventTimestamp: Instant?,
)

class SykmeldingHendelseRepository(
    private val databaseTransaction: DatabaseTransaction,
) {
    suspend fun upsert(hendelse: SykmeldingHendelse) {
        databaseTransaction.run {
            SykmeldingHendelseTable.upsert(SykmeldingHendelseTable.sykmeldingId) {
                it[sykmeldingId] = hendelse.sykmeldingId
                it[fnr] = hendelse.fnr
                it[organisasjonsnummer] = hendelse.organisasjonsnummer
                it[periodeFom] = hendelse.periodeFom
                it[periodeTom] = hendelse.periodeTom
                it[eventTimestamp] = hendelse.eventTimestamp?.atOffset(ZoneOffset.UTC)
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }
}
