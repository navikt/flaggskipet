package no.nav.flaggskipet.infrastructure.db

import org.jetbrains.exposed.v1.jdbc.upsert

data class SykmeldingKafkaInvalidMessage(
    val topic: String,
    val partition: Int,
    val recordOffset: Long,
    val errorCode: String,
    val sykmeldingId: String?,
)

class SykmeldingKafkaInvalidMessageRepository(
    private val databaseTransaction: DatabaseTransaction,
) {
    suspend fun upsert(message: SykmeldingKafkaInvalidMessage) {
        databaseTransaction.run {
            InvalidSykmeldingHendelseTable.upsert(
                InvalidSykmeldingHendelseTable.topic,
                InvalidSykmeldingHendelseTable.partition,
                InvalidSykmeldingHendelseTable.recordOffset,
            ) {
                it[topic] = message.topic
                it[partition] = message.partition
                it[recordOffset] = message.recordOffset
                it[errorCode] = message.errorCode
                it[sykmeldingId] = message.sykmeldingId
            }
        }
    }
}
