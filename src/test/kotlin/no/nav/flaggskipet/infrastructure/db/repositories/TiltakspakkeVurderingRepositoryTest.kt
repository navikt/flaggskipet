package no.nav.flaggskipet.infrastructure.db.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres

class TiltakspakkeVurderingRepositoryTest :
    FunSpec({
        test("lagrer og oppdaterer vurderinger per tiltakspakke og orgnummer") {
            withMigratedPostgres { dataSource, database ->
                val repository = TiltakspakkeVurderingRepositoryImpl(database)

                repository.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.TILTAKSGRUPPE,
                        ),
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_B",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                        ),
                    ),
                )

                repository.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.KONTROLLGRUPPE,
                        ),
                    ),
                )

                repository.hentVurderinger(
                    orgnumre = listOf("123456789"),
                    tiltakspakkeIder = listOf("PAKKE_A", "PAKKE_B"),
                ) shouldBe listOf(
                    Vurderingsresultat(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.KONTROLLGRUPPE,
                    ),
                    Vurderingsresultat(
                        tiltakspakkeId = "PAKKE_B",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.UTENFOR_SCOPE,
                    ),
                )

                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM tiltakspakke_deltakelse
                    """.trimIndent(),
                ) shouldBeExactly 2
            }
        }
    })
