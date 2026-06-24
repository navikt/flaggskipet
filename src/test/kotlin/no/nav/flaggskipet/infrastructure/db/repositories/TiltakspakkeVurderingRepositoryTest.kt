package no.nav.flaggskipet.infrastructure.db.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.infrastructure.dagensDato
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres
import java.time.LocalDate

class TiltakspakkeVurderingRepositoryTest :
    FunSpec({
        test("henter vurderinger for aktive tiltakspakker og filtrerte orgnumre") {
            withMigratedPostgres { dataSource, database ->
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        val today = LocalDate.parse(dagensDato())
                        val yesterday = today.minusDays(1)
                        val tomorrow = today.plusDays(1)

                        statement.executeUpdate(
                            """
                            INSERT INTO tiltakspakke (id, navn, slutt_dato)
                            VALUES
                                ('SYKEFRAVAERSOPPFOLGING', 'Sykefraværsoppfølging', NULL),
                                ('INKLUDERINGSDUGNAD', 'Inkluderingsdugnad', '$tomorrow'),
                                ('UTGAATT', 'Utgått pakke', '$yesterday')
                            """.trimIndent(),
                        )
                        statement.executeUpdate(
                            """
                            INSERT INTO tiltakspakke_deltakelse (tiltakspakke_id, orgnummer, deltakelse)
                            VALUES
                                ('SYKEFRAVAERSOPPFOLGING', '123456789', 'DELTAR'),
                                ('SYKEFRAVAERSOPPFOLGING', '987654321', 'DELTAR_IKKE'),
                                ('INKLUDERINGSDUGNAD', '123456789', 'UTENFOR_SCOPE'),
                                ('UTGAATT', '123456789', 'DELTAR')
                            """.trimIndent(),
                        )
                    }
                }

                val repository = TiltakspakkeVurderingRepositoryImpl(database)

                val vurderinger = repository.hentVurderinger(listOf("123456789", "987654321", "123456789"))

                vurderinger.map(TiltakspakkeVurdering::id) shouldBe
                    listOf("INKLUDERINGSDUGNAD", "SYKEFRAVAERSOPPFOLGING")

                vurderinger.first { it.id == "INKLUDERINGSDUGNAD" }.virksomheter.toSet() shouldBe
                    setOf(
                        VirksomhetDeltakelse(
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                        ),
                    )

                vurderinger.first { it.id == "SYKEFRAVAERSOPPFOLGING" }.virksomheter.toSet() shouldBe
                    setOf(
                        VirksomhetDeltakelse(
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.DELTAR,
                        ),
                        VirksomhetDeltakelse(
                            orgnummer = "987654321",
                            deltakelse = Deltakelse.DELTAR_IKKE,
                        ),
                    )
            }
        }
    })
