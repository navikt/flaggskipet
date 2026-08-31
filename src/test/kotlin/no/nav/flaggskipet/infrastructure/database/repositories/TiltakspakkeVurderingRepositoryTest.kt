package no.nav.flaggskipet.infrastructure.database.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.flaggskipet.application.port.VurderingForLagring
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Vurderingsgrunn
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat
import no.nav.flaggskipet.infrastructure.database.PostgresTestFixture
import no.nav.flaggskipet.infrastructure.database.config.transact
import no.nav.flaggskipet.infrastructure.database.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.sql.DriverManager
import java.sql.SQLException

class TiltakspakkeVurderingRepositoryTest :
    FunSpec({

        val fixture = PostgresTestFixture()
        beforeSpec { fixture.migrate() }
        afterTest { fixture.reset() }
        afterSpec { fixture.close() }

        suspend fun tiltakspakkeCount(): Long = fixture.database.transact {
            TiltakspakkeDeltakelseTable.selectAll().count()
        }

        test("beholder første vurdering per tiltakspakke og orgnummer") {
            with(fixture.database) {
                val repository = TiltakspakkeVurderingRepositoryImpl(this)
                repository.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.TILTAKSGRUPPE,
                            fylkeskode = "55",
                            vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_B",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                            fylkeskode = "50",
                            vurderingsgrunn = Vurderingsgrunn.FYLKE_UTENFOR_SCOPE,
                        ),
                    ),
                )

                repository.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.KONTROLLGRUPPE,
                            fylkeskode = "55",
                            vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                    ),
                ) shouldBe listOf(
                    Vurderingsresultat(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                    ),
                )

                repository.hentVurderinger(
                    orgnumre = listOf("123456789"),
                    tiltakspakkeIder = listOf("PAKKE_A", "PAKKE_B"),
                ) shouldBe listOf(
                    Vurderingsresultat(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                    ),
                    Vurderingsresultat(
                        tiltakspakkeId = "PAKKE_B",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.UTENFOR_SCOPE,
                        fylkeskode = "50",
                        vurderingsgrunn = Vurderingsgrunn.FYLKE_UTENFOR_SCOPE,
                    ),
                )
                tiltakspakkeCount() shouldBeExactly 2
            }
        }

        test("lagrer og henter fylkeskode og vurderingsgrunn") {
            with(fixture.database) {
                val repository = TiltakspakkeVurderingRepositoryImpl(this)
                repository.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.TILTAKSGRUPPE,
                            fylkeskode = "55",
                            vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                    ),
                )

                repository.hentVurderinger(
                    orgnumre = listOf("123456789"),
                    tiltakspakkeIder = listOf("PAKKE_A"),
                ) shouldBe listOf(
                    Vurderingsresultat(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                    ),
                )
            }
        }

        test("batchlagrer 100 vurderinger") {
            val repository = TiltakspakkeVurderingRepositoryImpl(fixture.database)
            val vurderinger = (1..100).map { indeks ->
                VurderingForLagring(
                    tiltakspakkeId = "PAKKE_A",
                    orgnummer = "100000${indeks.toString().padStart(3, '0')}",
                    deltakelse = Deltakelse.TILTAKSGRUPPE,
                    fylkeskode = "55",
                    vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                )
            }

            repository.lagreVurderinger(vurderinger).size shouldBe 100
            tiltakspakkeCount() shouldBeExactly 100
        }

        test("håndterer samtidige overlappende batcher med motsatt inputrekkefølge") {
            val repository = TiltakspakkeVurderingRepositoryImpl(fixture.database)
            val stigende = (1..20).map { indeks ->
                VurderingForLagring(
                    tiltakspakkeId = "PAKKE_A",
                    orgnummer = "100000${indeks.toString().padStart(3, '0')}",
                    deltakelse = Deltakelse.TILTAKSGRUPPE,
                    fylkeskode = "55",
                    vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                )
            }
            val synkende = stigende.reversed().map { it.copy(deltakelse = Deltakelse.KONTROLLGRUPPE) }
            val start = CompletableDeferred<Unit>()

            val resultater = coroutineScope {
                listOf(stigende, synkende)
                    .map { batch ->
                        async {
                            start.await()
                            repository.lagreVurderinger(batch)
                        }
                    }
                    .also { start.complete(Unit) }
                    .awaitAll()
            }

            resultater.map { resultat ->
                resultat.associate { (it.tiltakspakkeId to it.orgnummer) to it.deltakelse }
            }.distinct().size shouldBe 1
            tiltakspakkeCount() shouldBeExactly 20
        }

        test("avviser legacy-upsert uten vurderingsgrunn og bevarer korrekt rad") {
            val repository = TiltakspakkeVurderingRepositoryImpl(fixture.database)
            repository.lagreVurderinger(
                listOf(
                    VurderingForLagring(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.TILTAKSGRUPPE,
                        fylkeskode = "55",
                        vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
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
                        statement.setString(3, Deltakelse.KONTROLLGRUPPE.name)
                        statement.executeUpdate()
                    }
                }
            }

            exception.sqlState shouldBe "23502"
            repository.hentVurderinger(
                orgnumre = listOf("123456789"),
                tiltakspakkeIder = listOf("PAKKE_A"),
            ) shouldBe listOf(
                Vurderingsresultat(
                    tiltakspakkeId = "PAKKE_A",
                    orgnummer = "123456789",
                    deltakelse = Deltakelse.TILTAKSGRUPPE,
                    fylkeskode = "55",
                    vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                ),
            )
        }

        test("feiler tydelig ved ukjent vurderingsgrunn fra databasen") {
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
                    statement.setString(3, Deltakelse.UTENFOR_SCOPE.name)
                    statement.setString(4, "FREMTIDIG_VURDERINGSGRUNN")
                    statement.executeUpdate()
                }
            }

            shouldThrow<IllegalArgumentException> {
                TiltakspakkeVurderingRepositoryImpl(fixture.database).hentVurderinger(
                    orgnumre = listOf("987654321"),
                    tiltakspakkeIder = listOf("FREMTIDIG_TILTAKSPAKKE"),
                )
            }
        }
    })
