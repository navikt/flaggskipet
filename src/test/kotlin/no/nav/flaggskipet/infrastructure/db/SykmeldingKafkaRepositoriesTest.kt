package no.nav.flaggskipet.infrastructure.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

class SykmeldingKafkaRepositoriesTest :
    FunSpec({
        test("hendelse repository upserts idempotently on sykmelding id") {
            withMigratedPostgres { dataSource, database ->
                val repository = SykmeldingHendelseRepository(DatabaseTransaction(database))

                repository.upsert(
                    SykmeldingHendelse(
                        sykmeldingId = "event-1",
                        fnr = "00000000000",
                        organisasjonsnummer = "999888777",
                        periodeFom = LocalDate.parse("2026-01-01"),
                        periodeTom = LocalDate.parse("2026-01-10"),
                        eventTimestamp = Instant.parse("2026-01-15T10:15:30Z"),
                    ),
                )
                repository.upsert(
                    SykmeldingHendelse(
                        sykmeldingId = "event-1",
                        fnr = "00000000000",
                        organisasjonsnummer = "111222333",
                        periodeFom = LocalDate.parse("2026-02-01"),
                        periodeTom = LocalDate.parse("2026-02-10"),
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
                    periodeFom = "2026-02-01",
                    periodeTom = "2026-02-10",
                    eventTimestamp = Instant.parse("2026-02-15T10:15:30Z"),
                )
            }
        }

        test("invalid repository upserts idempotently on topic partition record offset") {
            withMigratedPostgres { dataSource, database ->
                val repository = SykmeldingKafkaInvalidMessageRepository(DatabaseTransaction(database))

                repository.upsert(
                    SykmeldingKafkaInvalidMessage(
                        topic = "teamsykmelding.syfo-sendt-sykmelding",
                        partition = 1,
                        recordOffset = 42L,
                        errorCode = "INVALID_CONTRACT",
                        sykmeldingId = null,
                    ),
                )
                repository.upsert(
                    SykmeldingKafkaInvalidMessage(
                        topic = "teamsykmelding.syfo-sendt-sykmelding",
                        partition = 1,
                        recordOffset = 42L,
                        errorCode = "INVALID_PERIOD",
                        sykmeldingId = "event-1",
                    ),
                )

                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_kafka_invalid_message
                    """.trimIndent(),
                ) shouldBeExactly 1
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_name = 'sykmelding_kafka_invalid_message'
                      AND column_name = 'key'
                    """.trimIndent(),
                ) shouldBeExactly 0

                dataSource.querySingleInvalidMessage() shouldBe InvalidRow(
                    topic = "teamsykmelding.syfo-sendt-sykmelding",
                    partition = 1,
                    recordOffset = 42L,
                    errorCode = "INVALID_PERIOD",
                    sykmeldingId = "event-1",
                )
            }
        }
    })

private data class HendelseRow(
    val sykmeldingId: String,
    val fnr: String,
    val organisasjonsnummer: String?,
    val periodeFom: String?,
    val periodeTom: String?,
    val eventTimestamp: Instant?,
)

private data class InvalidRow(
    val topic: String,
    val partition: Int,
    val recordOffset: Long,
    val errorCode: String,
    val sykmeldingId: String?,
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
                periodeFom = resultSet.getString("periode_fom"),
                periodeTom = resultSet.getString("periode_tom"),
                eventTimestamp = resultSet.getObject("event_timestamp", OffsetDateTime::class.java)?.toInstant(),
            )
        }
    }
}

private fun com.zaxxer.hikari.HikariDataSource.querySingleInvalidMessage(): InvalidRow = connection.use { connection ->
    connection.prepareStatement(
        """
        SELECT topic, partition, record_offset, error_code, sykmelding_id
        FROM sykmelding_kafka_invalid_message
        """.trimIndent(),
    ).use { statement ->
        statement.executeQuery().use { resultSet ->
            resultSet.next()
            InvalidRow(
                topic = resultSet.getString("topic"),
                partition = resultSet.getInt("partition"),
                recordOffset = resultSet.getLong("record_offset"),
                errorCode = resultSet.getString("error_code"),
                sykmeldingId = resultSet.getString("sykmelding_id"),
            )
        }
    }
}
