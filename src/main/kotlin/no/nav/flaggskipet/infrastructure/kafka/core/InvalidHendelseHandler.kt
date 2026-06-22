package no.nav.flaggskipet.infrastructure.kafka.core

import kotlinx.serialization.SerializationException
import no.nav.flaggskipet.infrastructure.db.core.InvalidHendelse
import no.nav.flaggskipet.infrastructure.db.repositories.InvalidHendelseRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class InvalidHendelseHandler(
    private val invalidHendelseRepository: InvalidHendelseRepository,
) : ConsumerErrorHandler<String, String?> {
    private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

    override suspend fun onError(record: ConsumerRecord<String, String?>, error: Exception) {
        logger.warn(
            "Storing invalid Kafka message for topic={}, partition={}, offset={}, errorCode={}",
            record.topic(),
            record.partition(),
            record.offset(),
            errorCode(error),
        )
        invalidHendelseRepository.upsert(
            InvalidHendelse(
                topic = record.topic(),
                partition = record.partition(),
                recordOffset = record.offset(),
                errorCode = errorCode(error),
                rawPayload = record.value(),
            ),
        )
    }

    private fun errorCode(error: Exception): String = when (error) {
        is SerializationException -> "INVALID_CONTRACT"
        else -> "HANDLER_ERROR"
    }
}
