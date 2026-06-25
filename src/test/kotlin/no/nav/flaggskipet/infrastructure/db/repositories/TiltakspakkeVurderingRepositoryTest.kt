package no.nav.flaggskipet.infrastructure.db.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres

class TiltakspakkeVurderingRepositoryTest :
    FunSpec({
        test("lagrer og oppdaterer vurderinger per tiltakspakke og orgnummer") {
            withMigratedPostgres { dataSource, database ->
                val repository = TiltakspakkeVurderingRepositoryImpl(database)

                repository.lagreVurderinger(
                    listOf(
                        NyTiltakspakkeVurdering(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.DELTAR,
                        ),
                        NyTiltakspakkeVurdering(
                            tiltakspakkeId = "PAKKE_B",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                        ),
                    ),
                )
                repository.lagreVurderinger(
                    listOf(
                        NyTiltakspakkeVurdering(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.DELTAR_IKKE,
                        ),
                    ),
                )

                repository.hentVurderinger(
                    orgnumre = listOf("123456789"),
                    tiltakspakkeIder = listOf("PAKKE_A", "PAKKE_B"),
                ) shouldBe listOf(
                    TiltakspakkeVurdering(
                        id = "PAKKE_A",
                        virksomheter = listOf(
                            VirksomhetDeltakelse(
                                orgnummer = "123456789",
                                deltakelse = Deltakelse.DELTAR_IKKE,
                            ),
                        ),
                    ),
                    TiltakspakkeVurdering(
                        id = "PAKKE_B",
                        virksomheter = listOf(
                            VirksomhetDeltakelse(
                                orgnummer = "123456789",
                                deltakelse = Deltakelse.UTENFOR_SCOPE,
                            ),
                        ),
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
