package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class SykmeldingHendelseDto(
    val sykmelding: ArbeidsgiverSykmelding,
    val kafkaMetadata: KafkaMetadata,
    val event: Event,
)

@Serializable
internal data class ArbeidsgiverSykmelding(
    val sykmeldingsperioder: List<SykmeldingsperiodeAgDto>,
    val syketilfelleStartDato: LocalDate?,
)

@Serializable
internal data class SykmeldingsperiodeAgDto(
    val fom: LocalDate,
    val tom: LocalDate,
)

@Serializable
internal data class KafkaMetadata(
    val sykmeldingId: String,
    val timestamp: Instant,
    val fnr: String,
    val source: String,
)

@Serializable
internal data class Event(
    val sykmeldingId: String,
    val timestamp: Instant,
    val arbeidsgiver: Arbeidsgiver? = null,
    val brukerSvar: BrukerSvar? = null,
)

@Serializable
internal data class BrukerSvar(
    val riktigNarmesteLeder: RiktigNarmesteLeder?,
)

@Serializable
internal data class RiktigNarmesteLeder(
    val sporsmaltekst: String,
    val svar: String,
)

@Serializable
internal data class Arbeidsgiver(
    val orgnummer: String,
    val juridiskOrgnummer: String? = null,
    val orgNavn: String,
)
