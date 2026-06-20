package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.infrastructure.db.DatabaseTransaction
import no.nav.flaggskipet.infrastructure.db.SykmeldingKafkaInvalidMessageRepository
import no.nav.flaggskipet.infrastructure.db.SykmeldingHendelseRepository
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres
import no.nav.flaggskipet.infrastructure.kafka.KafkaHandleResult
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.jdbc.Database

class SykmeldingKafkaMessageHandlerTest :
    FunSpec({
        test("handler stores valid sykmelding hendelse row and returns commit") {
            withMigratedPostgres { dataSource, database ->
                val handler = createHandler(database)

                val result = handler.handle(
                    ConsumerRecord(
                        "teamsykmelding.syfo-sendt-sykmelding",
                        0,
                        10L,
                        "sykmelding-key",
                        SykmeldingKafkaMessageFixtures.validMessage("event-1").encodeToByteArray(),
                    ),
                )

                result shouldBe KafkaHandleResult.COMMIT
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_hendelse
                    """.trimIndent(),
                ) shouldBeExactly 1
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_kafka_invalid_message
                    """.trimIndent(),
                ) shouldBeExactly 0
            }
        }

        test("handler stores invalid row and returns commit for permanent invalid message") {
            withMigratedPostgres { dataSource, database ->
                val handler = createHandler(database)

                val result = handler.handle(
                    ConsumerRecord(
                        "teamsykmelding.syfo-sendt-sykmelding",
                        1,
                        20L,
                        "sykmelding-key",
                        SykmeldingKafkaMessageFixtures.mismatchedSykmeldingIdMessage("event-2").encodeToByteArray(),
                    ),
                )

                result shouldBe KafkaHandleResult.COMMIT
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_hendelse
                    """.trimIndent(),
                ) shouldBeExactly 0
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_kafka_invalid_message
                    """.trimIndent(),
                ) shouldBeExactly 1
            }
        }

        test("handler commits tombstone without db writes") {
            withMigratedPostgres { dataSource, database ->
                val handler = createHandler(database)

                val result = handler.handle(
                    ConsumerRecord<String, ByteArray?>(
                        "teamsykmelding.syfo-sendt-sykmelding",
                        2,
                        30L,
                        "sykmelding-key",
                        null,
                    ),
                )

                result shouldBe KafkaHandleResult.COMMIT
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_hendelse
                    """.trimIndent(),
                ) shouldBeExactly 0
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_kafka_invalid_message
                    """.trimIndent(),
                ) shouldBeExactly 0
            }
        }

        test("handler propagates database failures for valid messages") {
            withMigratedPostgres { dataSource, database ->
                val handler = createHandler(database)
                dataSource.close()

                runCatching {
                    handler.handle(
                        ConsumerRecord(
                            "teamsykmelding.syfo-sendt-sykmelding",
                            0,
                            40L,
                            "sykmelding-key",
                            SykmeldingKafkaMessageFixtures.validMessage("event-3").encodeToByteArray(),
                        ),
                    )
                }.isFailure shouldBe true
            }
        }
    })

private fun createHandler(database: Database): SykmeldingKafkaMessageHandler = SykmeldingKafkaMessageHandler(
    decoder = SykmeldingKafkaMessageDecoder(),
    hendelseRepository = SykmeldingHendelseRepository(DatabaseTransaction(database)),
    invalidMessageRepository = SykmeldingKafkaInvalidMessageRepository(DatabaseTransaction(database)),
)
