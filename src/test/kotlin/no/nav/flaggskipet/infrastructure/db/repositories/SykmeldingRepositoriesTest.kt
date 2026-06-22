package no.nav.flaggskipet.infrastructure.db.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.infrastructure.db.core.Transaction
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres
import java.time.OffsetDateTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class SykmeldingKafkaRepositoriesTest :
    FunSpec({
        test("hendelse repository upserts idempotently on sykmelding id") {
            withMigratedPostgres { dataSource, database ->
                val repository = SykmeldingHendelseRepositoryImpl(Transaction(database))

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

                dataSource.querySingleHendelse() shouldBe HendelseRow(
                    sykmeldingId = "event-1",
                    fnr = "00000000000",
                    organisasjonsnummer = "111222333",
                    eventTimestamp = Instant.parse("2026-02-15T10:15:30Z"),
                )
            }
        }
    })

private data class HendelseRow(
    val sykmeldingId: String,
    val fnr: String,
    val organisasjonsnummer: String?,
    val eventTimestamp: Instant?,
)

private fun com.zaxxer.hikari.HikariDataSource.querySingleHendelse(): HendelseRow = connection.use { connection ->
    connection.prepareStatement(
        """
        SELECT sykmelding_id, fnr, organisasjonsnummer, periode_fom, periode_tom, event_timestamp
        FROM sykmelding_hendelse
        """.trimIndent(),
    ).use { statement ->
        statement.executeQuery().use { resultSet ->
            resultSet.next()
            HendelseRow(
                sykmeldingId = resultSet.getString("sykmelding_id"),
                fnr = resultSet.getString("fnr"),
                organisasjonsnummer = resultSet.getString("organisasjonsnummer"),
                eventTimestamp = resultSet.getObject("event_timestamp", OffsetDateTime::class.java)
                    ?.toInstant()
                    ?.toKotlinInstant(),
            )
        }
    }
}
