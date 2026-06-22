package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import kotlinx.serialization.json.Json
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelse
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepository
import no.nav.flaggskipet.infrastructure.kafka.core.MessageHandler
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class SykmeldingHendelseHandler(
    private val sykmeldingHendelseRepository: SykmeldingHendelseRepository,
) : MessageHandler<String, String?> {
    private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun handle(record: ConsumerRecord<String, String?>) {
        val payload = record.value() ?: return
        val dto = json.decodeFromString<SykmeldingHendelseDto>(payload)
        sykmeldingHendelseRepository.upsert(toSykmeldingHendelse(dto))
        logger.debug(
            "Processed sykmelding hendelse for topic={}, partition={}, offset={} sykmeldingId={}",
            record.topic(),
            record.partition(),
            record.offset(),
            dto.kafkaMetadata.sykmeldingId,
        )
    }

    private fun toSykmeldingHendelse(dto: SykmeldingHendelseDto): SykmeldingHendelse = SykmeldingHendelse(
        sykmeldingId = dto.kafkaMetadata.sykmeldingId,
        fnr = dto.kafkaMetadata.fnr,
        organisasjonsnummer = dto.event.arbeidsgiver?.orgnummer,
        eventTimestamp = dto.event.timestamp,
    )
}
