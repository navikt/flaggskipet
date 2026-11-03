package no.nav.flaggskipet.infrastructure.database.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.flaggskipet.application.port.NewAssessment
import no.nav.flaggskipet.domain.assessment.AssessmentReason
import no.nav.flaggskipet.domain.assessment.AssessmentResult
import no.nav.flaggskipet.domain.assessment.Participation
import no.nav.flaggskipet.infrastructure.database.PostgresTestFixture
import no.nav.flaggskipet.infrastructure.database.config.transact
import no.nav.flaggskipet.infrastructure.database.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.sql.DriverManager
import java.sql.SQLException

class TiltakspakkeAssessmentRepositoryTest :
    FunSpec({

        val fixture = PostgresTestFixture()
        beforeSpec { fixture.migrate() }
        afterTest { fixture.reset() }
        afterSpec { fixture.close() }

        suspend fun tiltakspakkeCount(): Long = fixture.database.transact {
            TiltakspakkeDeltakelseTable.selectAll().count()
        }

        test("keeps the first assessment per Tiltakspakke and orgnummer") {
            with(fixture.database) {
                val repository = TiltakspakkeAssessmentRepositoryImpl(this)
                repository.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            participation = Participation.TILTAKSGRUPPE,
                            fylkeskode = "55",
                            assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                        ),
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_B",
                            orgnummer = "123456789",
                            participation = Participation.UTENFOR_SCOPE,
                            fylkeskode = "50",
                            assessmentReason = AssessmentReason.FYLKE_UTENFOR_SCOPE,
                        ),
                    ),
                )

                repository.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            participation = Participation.KONTROLLGRUPPE,
                            fylkeskode = "55",
                            assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                        ),
                    ),
                ) shouldBe listOf(
                    AssessmentResult(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        participation = Participation.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                    ),
                )

                repository.getAssessments(
                    orgnumre = listOf("123456789"),
                    tiltakspakkeIds = listOf("PAKKE_A", "PAKKE_B"),
                ) shouldBe listOf(
                    AssessmentResult(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        participation = Participation.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                    ),
                    AssessmentResult(
                        tiltakspakkeId = "PAKKE_B",
                        orgnummer = "123456789",
                        participation = Participation.UTENFOR_SCOPE,
                        fylkeskode = "50",
                        assessmentReason = AssessmentReason.FYLKE_UTENFOR_SCOPE,
                    ),
                )
                tiltakspakkeCount() shouldBeExactly 2
            }
        }

        test("saves and retrieves fylkeskode and assessmentReason") {
            with(fixture.database) {
                val repository = TiltakspakkeAssessmentRepositoryImpl(this)
                repository.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            participation = Participation.TILTAKSGRUPPE,
                            fylkeskode = "55",
                            assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                        ),
                    ),
                )

                repository.getAssessments(
                    orgnumre = listOf("123456789"),
                    tiltakspakkeIds = listOf("PAKKE_A"),
                ) shouldBe listOf(
                    AssessmentResult(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        participation = Participation.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                    ),
                )
            }
        }

        test("saves a batch of 100 assessments") {
            val repository = TiltakspakkeAssessmentRepositoryImpl(fixture.database)
            val assessments = (1..100).map { index ->
                NewAssessment(
                    tiltakspakkeId = "PAKKE_A",
                    orgnummer = "100000${index.toString().padStart(3, '0')}",
                    participation = Participation.TILTAKSGRUPPE,
                    fylkeskode = "55",
                    assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                )
            }

            repository.saveAssessments(assessments).size shouldBe 100
            tiltakspakkeCount() shouldBeExactly 100
        }

        test("handles concurrent overlapping batches with opposite input order") {
            val repository = TiltakspakkeAssessmentRepositoryImpl(fixture.database)
            val ascending = (1..20).map { index ->
                NewAssessment(
                    tiltakspakkeId = "PAKKE_A",
                    orgnummer = "100000${index.toString().padStart(3, '0')}",
                    participation = Participation.TILTAKSGRUPPE,
                    fylkeskode = "55",
                    assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                )
            }
            val descending = ascending.reversed().map { it.copy(participation = Participation.KONTROLLGRUPPE) }
            val start = CompletableDeferred<Unit>()

            val results = coroutineScope {
                listOf(ascending, descending)
                    .map { batch ->
                        async {
                            start.await()
                            repository.saveAssessments(batch)
                        }
                    }
                    .also { start.complete(Unit) }
                    .awaitAll()
            }

            results.map { result ->
                result.associate { (it.tiltakspakkeId to it.orgnummer) to it.participation }
            }.distinct().size shouldBe 1
            tiltakspakkeCount() shouldBeExactly 20
        }

        test("rejects legacy upsert without assessmentReason and preserves the existing row") {
            val repository = TiltakspakkeAssessmentRepositoryImpl(fixture.database)
            repository.saveAssessments(
                listOf(
                    NewAssessment(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        participation = Participation.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                    ),
                ),
            )

            val exception = shouldThrow<SQLException> {
                DriverManager.getConnection(fixture.jdbcUrl, fixture.username, fixture.password).use { connection ->
                    connection.prepareStatement(
                        """
                            INSERT INTO tiltakspakke_deltakelse (tiltakspakke_id, orgnummer, deltakelse)
                            VALUES (?, ?, ?)
                            ON CONFLICT (tiltakspakke_id, orgnummer)
                            DO UPDATE SET deltakelse = EXCLUDED.deltakelse
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, "PAKKE_A")
                        statement.setString(2, "123456789")
                        statement.setString(3, Participation.KONTROLLGRUPPE.name)
                        statement.executeUpdate()
                    }
                }
            }

            exception.sqlState shouldBe "23502"
            repository.getAssessments(
                orgnumre = listOf("123456789"),
                tiltakspakkeIds = listOf("PAKKE_A"),
            ) shouldBe listOf(
                AssessmentResult(
                    tiltakspakkeId = "PAKKE_A",
                    orgnummer = "123456789",
                    participation = Participation.TILTAKSGRUPPE,
                    fylkeskode = "55",
                    assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                ),
            )
        }

        test("fails clearly for an unknown assessmentReason from the database") {
            DriverManager.getConnection(fixture.jdbcUrl, fixture.username, fixture.password).use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO tiltakspakke_deltakelse
                        (tiltakspakke_id, orgnummer, deltakelse, vurderingsgrunn)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, "FREMTIDIG_TILTAKSPAKKE")
                    statement.setString(2, "987654321")
                    statement.setString(3, Participation.UTENFOR_SCOPE.name)
                    statement.setString(4, "FREMTIDIG_VURDERINGSGRUNN")
                    statement.executeUpdate()
                }
            }

            shouldThrow<IllegalArgumentException> {
                TiltakspakkeAssessmentRepositoryImpl(fixture.database).getAssessments(
                    orgnumre = listOf("987654321"),
                    tiltakspakkeIds = listOf("FREMTIDIG_TILTAKSPAKKE"),
                )
            }
        }
    })
