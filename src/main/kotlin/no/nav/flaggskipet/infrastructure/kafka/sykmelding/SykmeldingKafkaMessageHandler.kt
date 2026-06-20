package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import no.nav.flaggskipet.infrastructure.db.SykmeldingHendelse
import no.nav.flaggskipet.infrastructure.db.SykmeldingHendelseRepository
import no.nav.flaggskipet.infrastructure.db.SykmeldingKafkaInvalidMessage
import no.nav.flaggskipet.infrastructure.db.SykmeldingKafkaInvalidMessageRepository
import no.nav.flaggskipet.infrastructure.kafka.KafkaHandleResult
import no.nav.flaggskipet.infrastructure.kafka.KafkaMessageHandler
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class SykmeldingKafkaMessageHandler(
    private val decoder: SykmeldingKafkaMessageDecoder,
    private val hendelseRepository: SykmeldingHendelseRepository,
    private val invalidMessageRepository: SykmeldingKafkaInvalidMessageRepository,
) : KafkaMessageHandler<String, ByteArray?> {
    private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

    override suspend fun handle(record: ConsumerRecord<String, ByteArray?>): KafkaHandleResult {
        val metadata = record.kafkaMetadata()

        return when (val result = decoder.decode(record.value())) {
            SykmeldingKafkaMessageDecodeResult.Tombstone -> {
                logger.debug(
                    "Ignoring tombstone sykmelding message for topic={}, partition={}, offset={}",
                    metadata.topic,
                    metadata.partition,
                    metadata.offset,
                )
                KafkaHandleResult.COMMIT
            }

            is SykmeldingKafkaMessageDecodeResult.Valid -> {
                hendelseRepository.upsert(
                    SykmeldingHendelse(
                        sykmeldingId = result.message.sykmeldingId,
                        fnr = result.message.fnr,
                        organisasjonsnummer = result.message.organisasjonsnummer,
                        periodeFom = result.message.periodeFom,
                        periodeTom = result.message.periodeTom,
                        eventTimestamp = result.message.eventTimestamp,
                    ),
                )
                KafkaHandleResult.COMMIT
            }

            is SykmeldingKafkaMessageDecodeResult.Invalid -> {
                logger.warn(
                    "Persisting invalid sykmelding Kafka message for topic={}, partition={}, offset={}, sykmelding_id={}, error_code={}",
                    metadata.topic,
                    metadata.partition,
                    metadata.offset,
                    result.sykmeldingId,
                    result.reason,
                )
                invalidMessageRepository.upsert(
                    SykmeldingKafkaInvalidMessage(
                        topic = metadata.topic,
                        partition = metadata.partition,
                        recordOffset = metadata.offset,
                        errorCode = result.reason.name,
                        sykmeldingId = result.sykmeldingId,
                    ),
                )
                KafkaHandleResult.COMMIT
            }
        }
    }
}

private data class KafkaRecordProcessingMetadata(
    val topic: String,
    val partition: Int,
    val offset: Long,
)

private fun ConsumerRecord<String, ByteArray?>.kafkaMetadata(): KafkaRecordProcessingMetadata = KafkaRecordProcessingMetadata(
    topic = topic(),
    partition = partition(),
    offset = offset(),
)
