package no.nav.flaggskipet.infrastructure.database.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.application.port.VurderingForLagring
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat
import no.nav.flaggskipet.infrastructure.database.PostgresTestFixture
import no.nav.flaggskipet.infrastructure.database.config.transact
import no.nav.flaggskipet.infrastructure.database.tables.TiltakspakkeDeltakelseTable
import org.jetbrains.exposed.v1.jdbc.selectAll

class TiltakspakkeVurderingRepositoryTest :
    FunSpec({

        val fixture = PostgresTestFixture()
        beforeSpec { fixture.migrate() }
        afterTest { fixture.reset() }
        afterSpec { fixture.close() }

        suspend fun tiltakspakkeCount(): Long = fixture.database.transact {
            TiltakspakkeDeltakelseTable.selectAll().count()
        }

        test("lagrer og oppdaterer vurderinger per tiltakspakke og orgnummer") {
            with(fixture.database) {
                val repository = TiltakspakkeVurderingRepositoryImpl(this)
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
                tiltakspakkeCount() shouldBeExactly 2
            }
        }
    })
