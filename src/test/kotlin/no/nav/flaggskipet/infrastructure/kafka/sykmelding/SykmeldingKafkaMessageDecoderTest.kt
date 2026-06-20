package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class SykmeldingKafkaMessageDecoderTest :
    FunSpec({
        val decoder = SykmeldingKafkaMessageDecoder()

        test("decoder extracts minimum fields from valid sykmelding message") {
            val result = decoder.decode(
                SykmeldingKafkaMessageFixtures.validMessage(
                    sykmeldingId = "event-1",
                    timestamp = "2026-01-15T10:15:30Z",
                ).encodeToByteArray(),
            )

            result shouldBe SykmeldingKafkaMessageDecodeResult.Valid(
                DecodedSykmeldingKafkaMessage(
                    sykmeldingId = "event-1",
                    fnr = "00000000000",
                    organisasjonsnummer = "999888777",
                    periodeFom = LocalDate.parse("2026-01-01"),
                    periodeTom = LocalDate.parse("2026-01-10"),
                    eventTimestamp = Instant.parse("2026-01-15T10:15:30Z"),
                ),
            )
        }

        test("decoder accepts missing periods and stores null range") {
            val result = decoder.decode(
                """
                {
                  "kafkaMetadata": {
                    "sykmeldingId": "event-2",
                    "fnr": "00000000000"
                  },
                  "event": {}
                }
                """.trimIndent().encodeToByteArray(),
            )

            result shouldBe SykmeldingKafkaMessageDecodeResult.Valid(
                DecodedSykmeldingKafkaMessage(
                    sykmeldingId = "event-2",
                    fnr = "00000000000",
                    organisasjonsnummer = null,
                    periodeFom = null,
                    periodeTom = null,
                    eventTimestamp = null,
                ),
            )
        }

        test("decoder returns invalid for mismatched sykmelding id") {
            val result = decoder.decode(
                SykmeldingKafkaMessageFixtures.mismatchedSykmeldingIdMessage("event-3").encodeToByteArray(),
            )

            result shouldBe SykmeldingKafkaMessageDecodeResult.Invalid(
                reason = SykmeldingKafkaMessageInvalidReason.MISMATCHED_SYKMELDING_ID,
                sykmeldingId = "event-3",
            )
        }

        test("decoder returns invalid for malformed period") {
            val result = decoder.decode(
                SykmeldingKafkaMessageFixtures.invalidPeriodMessage("event-4").encodeToByteArray(),
            )

            result shouldBe SykmeldingKafkaMessageDecodeResult.Invalid(
                reason = SykmeldingKafkaMessageInvalidReason.INVALID_PERIOD,
                sykmeldingId = null,
            )
        }

        test("decoder returns invalid for malformed json") {
            val result = decoder.decode("""{"kafkaMetadata":""".encodeToByteArray())

            result shouldBe SykmeldingKafkaMessageDecodeResult.Invalid(
                reason = SykmeldingKafkaMessageInvalidReason.INVALID_CONTRACT,
                sykmeldingId = null,
            )
        }

        test("decoder treats null payload as tombstone") {
            decoder.decode(null) shouldBe SykmeldingKafkaMessageDecodeResult.Tombstone
        }
    })
