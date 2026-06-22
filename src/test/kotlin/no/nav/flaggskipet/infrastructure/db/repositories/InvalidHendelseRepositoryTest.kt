package no.nav.flaggskipet.infrastructure.db.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.infrastructure.db.core.InvalidHendelse
import no.nav.flaggskipet.infrastructure.db.core.Transaction
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres

class InvalidHendelseRepositoryTest :
    FunSpec({
        test("upserts idempotently on topic partition record offset") {
            withMigratedPostgres { dataSource, database ->
                val repository = InvalidHendelseRepositoryImpl(Transaction(database))

                repository.upsert(
                    InvalidHendelse(
                        topic = "teamsykmelding.syfo-sendt-sykmelding",
                        partition = 1,
                        recordOffset = 42L,
                        errorCode = "INVALID_CONTRACT",
                        rawPayload = null,
                    ),
                )
                repository.upsert(
                    InvalidHendelse(
                        topic = "teamsykmelding.syfo-sendt-sykmelding",
                        partition = 1,
                        recordOffset = 42L,
                        errorCode = "INVALID_STATUS",
                        rawPayload = "bad json",
                    ),
                )

                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM invalid_event
                    """.trimIndent(),
                ) shouldBeExactly 1
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_name = 'invalid_event'
                      AND column_name = 'key'
                    """.trimIndent(),
                ) shouldBeExactly 0

                dataSource.querySingleInvalidMessage() shouldBe InvalidOrderRow(
                    topic = "teamsykmelding.syfo-sendt-sykmelding",
                    partition = 1,
                    recordOffset = 42L,
                    errorCode = "INVALID_STATUS",
                    rawPayload = "bad json",
                )
            }
        }
    })

private data class InvalidOrderRow(
    val topic: String,
    val partition: Int,
    val recordOffset: Long,
    val errorCode: String,
    val rawPayload: String?,
)

private fun com.zaxxer.hikari.HikariDataSource.querySingleInvalidMessage(): InvalidOrderRow = connection.use { connection ->
    connection.prepareStatement(
        """
        SELECT topic, partition, record_offset, error_code, raw_payload
        FROM invalid_hendelse
        """.trimIndent(),
    ).use { statement ->
        statement.executeQuery().use { resultSet ->
            resultSet.next()
            InvalidOrderRow(
                topic = resultSet.getString("topic"),
                partition = resultSet.getInt("partition"),
                recordOffset = resultSet.getLong("record_offset"),
                errorCode = resultSet.getString("error_code"),
                rawPayload = resultSet.getString("raw_payload"),
            )
        }
    }
}
