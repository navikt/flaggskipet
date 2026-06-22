package no.nav.flaggskipet.infrastructure.db.repositories

import no.nav.flaggskipet.infrastructure.db.core.InvalidHendelse
import no.nav.flaggskipet.infrastructure.db.core.Transaction
import no.nav.flaggskipet.infrastructure.db.tables.InvalidHendelseTable
import org.jetbrains.exposed.v1.jdbc.upsert

interface InvalidHendelseRepository {
    suspend fun upsert(event: InvalidHendelse)
}

class InvalidHendelseRepositoryImpl(
    private val transaction: Transaction,
) : InvalidHendelseRepository {
    override suspend fun upsert(event: InvalidHendelse) {
        transaction.run {
            InvalidHendelseTable.upsert(
                InvalidHendelseTable.topic,
                InvalidHendelseTable.partition,
                InvalidHendelseTable.recordOffset,
            ) {
                it[topic] = event.topic
                it[partition] = event.partition
                it[recordOffset] = event.recordOffset
                it[errorCode] = event.errorCode
                it[rawPayload] = event.rawPayload
            }
        }
    }
}
