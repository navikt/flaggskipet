package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

data class DecodedSykmeldingKafkaMessage(
    val sykmeldingId: String,
    val fnr: String,
    val organisasjonsnummer: String?,
    val periodeFom: LocalDate?,
    val periodeTom: LocalDate?,
    val eventTimestamp: Instant?,
)

enum class SykmeldingKafkaMessageInvalidReason {
    INVALID_CONTRACT,
    MISMATCHED_SYKMELDING_ID,
    INVALID_PERIOD,
}

sealed interface SykmeldingKafkaMessageDecodeResult {
    data object Tombstone : SykmeldingKafkaMessageDecodeResult

    data class Valid(
        val message: DecodedSykmeldingKafkaMessage,
    ) : SykmeldingKafkaMessageDecodeResult

    data class Invalid(
        val reason: SykmeldingKafkaMessageInvalidReason,
        val sykmeldingId: String?,
    ) : SykmeldingKafkaMessageDecodeResult
}

class SykmeldingKafkaMessageDecoder(
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) {
    fun decode(payload: ByteArray?): SykmeldingKafkaMessageDecodeResult = when (payload) {
        null -> SykmeldingKafkaMessageDecodeResult.Tombstone
        else -> runCatching {
            json.decodeFromString<SendtSykmeldingKafkaMessageDto>(payload.decodeToString())
                .toDecodedMessage()
        }.fold(
            onSuccess = SykmeldingKafkaMessageDecodeResult::Valid,
            onFailure = { error -> error.toInvalidResult() },
        )
    }
}

private fun SendtSykmeldingKafkaMessageDto.toDecodedMessage(): DecodedSykmeldingKafkaMessage {
    val sykmeldingId = kafkaMetadata.sykmeldingId.requiredNonBlank(sykmeldingId = null)
    val fnr = kafkaMetadata.fnr.requiredNonBlank(sykmeldingId = sykmeldingId)
    val eventSykmeldingId = event?.sykmeldingId.normalized()

    if (eventSykmeldingId != null && eventSykmeldingId != sykmeldingId) {
        throw InvalidSykmeldingKafkaMessage(
            reason = SykmeldingKafkaMessageInvalidReason.MISMATCHED_SYKMELDING_ID,
            sykmeldingId = sykmeldingId,
        )
    }

    val periodRange = sykmelding?.sykmeldingsperioder.toPeriodRange(sykmeldingId)

    return DecodedSykmeldingKafkaMessage(
        sykmeldingId = sykmeldingId,
        fnr = fnr,
        organisasjonsnummer = event?.arbeidsgiver?.orgnummer.normalized(),
        periodeFom = periodRange?.fom,
        periodeTom = periodRange?.tom,
        eventTimestamp = kafkaMetadata.timestamp?.let(::parseTimestampOrNull),
    )
}

private fun List<SykmeldingsperiodeDto>?.toPeriodRange(sykmeldingId: String): PeriodRange? {
    if (isNullOrEmpty()) {
        return null
    }

    val parsedPeriods = map { periode ->
        val fom = periode.fom.normalized()?.let(::parseDateOrNull)
            ?: throw InvalidSykmeldingKafkaMessage(SykmeldingKafkaMessageInvalidReason.INVALID_PERIOD, sykmeldingId)
        val tom = periode.tom.normalized()?.let(::parseDateOrNull)
            ?: throw InvalidSykmeldingKafkaMessage(SykmeldingKafkaMessageInvalidReason.INVALID_PERIOD, sykmeldingId)

        fom to tom
    }

    return PeriodRange(
        fom = parsedPeriods.minOf { it.first },
        tom = parsedPeriods.maxOf { it.second },
    )
}

private fun String.requiredNonBlank(sykmeldingId: String?): String =
    normalized() ?: throw InvalidSykmeldingKafkaMessage(SykmeldingKafkaMessageInvalidReason.INVALID_CONTRACT, sykmeldingId)

private fun Throwable.toInvalidResult(): SykmeldingKafkaMessageDecodeResult.Invalid = when (this) {
    is InvalidSykmeldingKafkaMessage -> SykmeldingKafkaMessageDecodeResult.Invalid(reason, sykmeldingId)
    is SerializationException, is IllegalArgumentException -> SykmeldingKafkaMessageDecodeResult.Invalid(
        reason = SykmeldingKafkaMessageInvalidReason.INVALID_CONTRACT,
        sykmeldingId = null,
    )

    else -> throw this
}

private data class PeriodRange(
    val fom: LocalDate,
    val tom: LocalDate,
)

private class InvalidSykmeldingKafkaMessage(
    val reason: SykmeldingKafkaMessageInvalidReason,
    val sykmeldingId: String?,
) : RuntimeException()

@Serializable
private data class SendtSykmeldingKafkaMessageDto(
    val kafkaMetadata: KafkaMetadataDto,
    val event: SykmeldingEventDto? = null,
    val sykmelding: SykmeldingDto? = null,
)

@Serializable
private data class KafkaMetadataDto(
    val sykmeldingId: String,
    val fnr: String,
    val timestamp: String? = null,
)

@Serializable
private data class SykmeldingEventDto(
    val sykmeldingId: String? = null,
    val arbeidsgiver: ArbeidsgiverDto? = null,
)

@Serializable
private data class ArbeidsgiverDto(
    val orgnummer: String? = null,
)

@Serializable
private data class SykmeldingDto(
    val sykmeldingsperioder: List<SykmeldingsperiodeDto>? = null,
)

@Serializable
private data class SykmeldingsperiodeDto(
    val fom: String,
    val tom: String,
)

private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotBlank() }

private fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private fun parseTimestampOrNull(value: String): Instant? = runCatching {
    OffsetDateTime.parse(value).toInstant()
}.recoverCatching {
    Instant.parse(value)
}.getOrNull()
