package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import no.nav.flaggskipet.infrastructure.db.core.Transaction
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.repositories.SykmeldingHendelseRepositoryImpl
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.jdbc.Database

@OptIn(ExperimentalSerializationApi::class)
class SykmeldingHendelseHandlerTest :
    FunSpec({
        test("handler stores valid sykmelding hendelse row and returns commit") {
            withMigratedPostgres { dataSource, database ->
                val handler = createHandler(database)

                handler.handle(
                    ConsumerRecord(
                        "teamsykmelding.syfo-sendt-sykmelding",
                        0,
                        10L,
                        "sykmelding-key",
                        SykmeldingHendelseFixtures.validMessage(),
                    ),
                )

                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_hendelse
                    """.trimIndent(),
                ) shouldBeExactly 1
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM invalid_hendelse
                    """.trimIndent(),
                ) shouldBeExactly 0
            }
        }

        test("handler will throw exception") {
            withMigratedPostgres { _, database ->
                shouldThrow<MissingFieldException> {
                    createHandler(database).handle(
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
        }

        test("handler commits tombstone without db writes") {
            withMigratedPostgres { dataSource, database ->
                val handler = createHandler(database)

                handler.handle(
                    ConsumerRecord(
                        "teamsykmelding.syfo-sendt-sykmelding",
                        2,
                        30L,
                        "sykmelding-key",
                        null,
                    ),
                )

                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM sykmelding_hendelse
                    """.trimIndent(),
                ) shouldBeExactly 0
                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM invalid_hendelse
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
                            SykmeldingHendelseFixtures.validMessage(),
                        ),
                    )
                }.isFailure shouldBe true
            }
        }
    })

private fun createHandler(database: Database): SykmeldingHendelseHandler = SykmeldingHendelseHandler(
    sykmeldingHendelseRepository = SykmeldingHendelseRepositoryImpl(Transaction(database)),
)
