package no.nav.flaggskipet.domain.vurdering

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.flaggskipet.infrastructure.clients.ereg.EregClient
import no.nav.flaggskipet.infrastructure.clients.ereg.EregNoekkelinfo
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.infrastructure.db.repositories.VurderingForLagring

class VurderTiltakspakkerUseCaseTest :
    FunSpec({

        val adresseI50 = Adresse(type = "Forretningsadresse", postnummer = "7004", kommunenummer = "5001")
        val tiltakspakkeA = Tiltakspakke(
            id = "PAKKE_A",
            regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 1.0),
        )
        val tiltakspakkeB = Tiltakspakke(
            id = "PAKKE_B",
            regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 1.0),
        )

        test("returns empty list when no tiltakspakker") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeVurderingRepository>(relaxed = true)
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = emptyList()).shouldBeEmpty()

            coVerify(exactly = 0) { ereg.hentNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.hentVurderinger(any(), any()) }
            coVerify(exactly = 0) { repo.lagreVurderinger(any()) }
        }

        test("returns existing vurderinger without calling ereg") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns listOf(
                    Vurderingsresultat("PAKKE_A", "123", Deltakelse.TILTAKSGRUPPE),
                )
            }
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.TILTAKSGRUPPE))),
            )

            coVerify(exactly = 0) { ereg.hentNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.lagreVurderinger(any()) }
        }

        test("evaluates new orgnumre and saves vurderinger") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", adresseI50),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
                coEvery { lagreVurderinger(any()) } returns Unit
            }
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.TILTAKSGRUPPE))),
            )

            coVerify { ereg.hentNoekkelinfo(listOf("123")) }
            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring("PAKKE_A", "123", Deltakelse.TILTAKSGRUPPE),
                    ),
                )
            }
        }

        test("evaluates to UTENFOR_SCOPE when ereg returns no adresse") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", null),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
                coEvery { lagreVurderinger(any()) } returns Unit
            }
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.UTENFOR_SCOPE))),
            )

            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring("PAKKE_A", "123", Deltakelse.UTENFOR_SCOPE),
                    ),
                )
            }
        }

        test("only evaluates new orgnumre, skips existing ones") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns listOf(
                    Vurderingsresultat("PAKKE_A", "111", Deltakelse.TILTAKSGRUPPE),
                )
                coEvery { lagreVurderinger(any()) } returns Unit
            }
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("111", "222"), tiltakspakker = listOf(tiltakspakkeA))

            coVerify { ereg.hentNoekkelinfo(listOf("222")) }
            coVerify(exactly = 0) { ereg.hentNoekkelinfo(listOf("111")) }
        }

        test("handles multiple tiltakspakker and orgnumre with correct grouping") {
            val adresse = Adresse("Forretningsadresse", "7004", "5001")
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("111", "222")) } returns listOf(
                    EregNoekkelinfo("111", adresse),
                    EregNoekkelinfo("222", adresse),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
                coEvery { lagreVurderinger(any()) } returns Unit
            }
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("111", "222"), tiltakspakker = listOf(tiltakspakkeA, tiltakspakkeB)) shouldBe listOf(
                TiltakspakkeVurdering(
                    "PAKKE_A",
                    listOf(
                        VirksomhetDeltakelse("111", Deltakelse.TILTAKSGRUPPE),
                        VirksomhetDeltakelse("222", Deltakelse.TILTAKSGRUPPE),
                    ),
                ),
                TiltakspakkeVurdering(
                    "PAKKE_B",
                    listOf(
                        VirksomhetDeltakelse("111", Deltakelse.TILTAKSGRUPPE),
                        VirksomhetDeltakelse("222", Deltakelse.TILTAKSGRUPPE),
                    ),
                ),
            )

            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring("PAKKE_A", "111", Deltakelse.TILTAKSGRUPPE),
                        VurderingForLagring("PAKKE_B", "111", Deltakelse.TILTAKSGRUPPE),
                        VurderingForLagring("PAKKE_A", "222", Deltakelse.TILTAKSGRUPPE),
                        VurderingForLagring("PAKKE_B", "222", Deltakelse.TILTAKSGRUPPE),
                    ),
                )
            }
        }
    })
