package no.nav.flaggskipet.infrastructure.db.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres
import kotlin.time.Instant

class SykmeldingRepositoriesTest :
    FunSpec({
        test("hendelse repository upserts idempotently on sykmelding id") {
            withMigratedPostgres { dataSource, database ->
                val repository = SykmeldingHendelseRepositoryImpl(database)

                repository.upsert(
                    SykmeldingHendelse(
                        sykmeldingId = "event-1",
                        fnr = "00000000000",
                        organisasjonsnummer = "999888777",
                        eventTimestamp = Instant.parse("2026-01-15T10:15:30Z"),
                    ),
                )
                repository.upsert(
                    SykmeldingHendelse(
                        sykmeldingId = "event-1",
                        fnr = "00000000000",
                        organisasjonsnummer = "111222333",
                        eventTimestamp = Instant.parse("2026-02-15T10:15:30Z"),
                    ),
                )

                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_hendelse
                    """.trimIndent(),
                ) shouldBeExactly 1
            }
        }
    })
