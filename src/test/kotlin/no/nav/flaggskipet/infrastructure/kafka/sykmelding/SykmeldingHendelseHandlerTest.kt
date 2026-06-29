package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelse
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
class SykmeldingHendelseHandlerTest :
    FunSpec({
        test("handler lagrer gyldig sykmelding hendelse") {
            val repo = mockk<SykmeldingHendelseRepository>()
            coEvery { repo.upsert(any()) } returns Unit

            val handler = SykmeldingHendelseHandler(repo)
            handler.handle(
                ConsumerRecord(
                    "teamsykmelding.syfo-sendt-sykmelding",
                    0,
                    10L,
                    "sykmelding-key",
                    SykmeldingHendelseFixtures.validMessage(),
                ),
            )

            coVerify(exactly = 1) {
                repo.upsert(
                    SykmeldingHendelse(
                        sykmeldingId = "sm-123456789",
                        fnr = "12039456789",
                        organisasjonsnummer = "987654321",
                        eventTimestamp = Instant.parse("2026-06-22T10:15:30Z"),
                    ),
                )
            }
        }

        test("handler kaster med ugyldig melding") {
            val repo = mockk<SykmeldingHendelseRepository>(relaxed = true)
            val handler = SykmeldingHendelseHandler(repo)

            shouldThrow<MissingFieldException> {
                handler.handle(
                    ConsumerRecord(
                        "teamsykmelding.syfo-sendt-sykmelding",
                        0,
                        10L,
                        "sykmelding-key",
                        SykmeldingHendelseFixtures.mismatchedSykmeldingIdMessage(),
                    ),
                )
            }
        }

        test("handler committer tombstone uten db-skrivinger") {
            val repo = mockk<SykmeldingHendelseRepository>(relaxed = true)
            val handler = SykmeldingHendelseHandler(repo)

            handler.handle(
                ConsumerRecord(
                    "teamsykmelding.syfo-sendt-sykmelding",
                    2,
                    30L,
                    "sykmelding-key",
                    null,
                ),
            )

            coVerify(exactly = 0) { repo.upsert(any()) }
        }

        test("handler propagerer repository feil") {
            val repo = mockk<SykmeldingHendelseRepository>()
            coEvery { repo.upsert(any()) } throws RuntimeException("db failure")

            val handler = SykmeldingHendelseHandler(repo)

            runCatching {
                handler.handle(
                    ConsumerRecord(
                        "teamsykmelding.syfo-sendt-sykmelding",
                        0,
                        40L,
                        "sykmelding-key",
                        SykmeldingHendelseFixtures.validMessage(),
                    ),
                )
            }.let {
                it.isFailure shouldBe true
                it.exceptionOrNull()?.message shouldBe "db failure"
            }
        }
    })
