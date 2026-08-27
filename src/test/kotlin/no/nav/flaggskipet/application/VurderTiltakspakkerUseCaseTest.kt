package no.nav.flaggskipet.application

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.application.port.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.application.port.VurderingForLagring
import no.nav.flaggskipet.domain.vurdering.Adresse
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.GeoTiltakspakkeRegel
import no.nav.flaggskipet.domain.vurdering.Tiltakspakke
import no.nav.flaggskipet.domain.vurdering.TiltakspakkeVurdering
import no.nav.flaggskipet.domain.vurdering.VirksomhetDeltakelse
import no.nav.flaggskipet.domain.vurdering.Vurderingsgrunn
import no.nav.flaggskipet.domain.vurdering.Vurderingsresultat

class VurderTiltakspakkerUseCaseTest :
    FunSpec({

        val adresseI50 = Adresse(type = "Forretningsadresse", postnummer = "7004", kommunenummer = "5001")
        val tiltakspakkeA = Tiltakspakke(
            id = "PAKKE_A",
            regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), andelTilTiltaksgruppe = 1.0, tiltakspakkeId = "PAKKE_A"),
        )
        val tiltakspakkeB = Tiltakspakke(
            id = "PAKKE_B",
            regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), andelTilTiltaksgruppe = 1.0, tiltakspakkeId = "PAKKE_B"),
        )

        test("returnerer tom liste når det ikke finnes noen tiltakspakker") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeVurderingRepository>(relaxed = true)
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = emptyList()).shouldBeEmpty()

            coVerify(exactly = 0) { ereg.hentNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.hentVurderinger(any(), any()) }
            coVerify(exactly = 0) { repo.lagreVurderinger(any()) }
        }

        test("returnerer eksisterende vurderinger uten å kalle ereg") {
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

        test("evaluerer nye orgnumre og lagrer vurderinger") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", adresseI50),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
            }
            repo.returnererLagredeVurderinger()
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.TILTAKSGRUPPE))),
            )

            coVerify { ereg.hentNoekkelinfo(listOf("123")) }
            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            deltakelse = Deltakelse.TILTAKSGRUPPE,
                            fylkeskode = "50",
                            vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                    ),
                )
            }
        }

        test("evaluerer til UTENFOR_SCOPE når ereg returnerer ingen adresse") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", null),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
            }
            repo.returnererLagredeVurderinger()
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.UTENFOR_SCOPE))),
            )

            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                            fylkeskode = null,
                            vurderingsgrunn = Vurderingsgrunn.MANGLER_ADRESSE,
                        ),
                    ),
                )
            }
        }

        test("evaluerer til UTENFOR_SCOPE når adressen mangler gyldig kommunenummer") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo(
                        "123",
                        Adresse(type = "Forretningsadresse", postnummer = "", kommunenummer = ""),
                    ),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
            }
            repo.returnererLagredeVurderinger()
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.UTENFOR_SCOPE))),
            )

            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                            fylkeskode = null,
                            vurderingsgrunn = Vurderingsgrunn.UGYLDIG_KOMMUNENUMMER,
                        ),
                    ),
                )
            }
        }

        test("lagrer fylket som grunn når virksomheten er utenfor geografisk scope") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", Adresse("Forretningsadresse", "9008", "5501")),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
            }
            repo.returnererLagredeVurderinger()

            VurderTiltakspakkerUseCase(ereg, repo).execute(listOf("123"), listOf(tiltakspakkeA))

            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                            fylkeskode = "55",
                            vurderingsgrunn = Vurderingsgrunn.FYLKE_UTENFOR_SCOPE,
                        ),
                    ),
                )
            }
        }

        test("markerer utgått fylkeskode 54 eksplisitt") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", Adresse("Forretningsadresse", "9008", "5401")),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
            }
            repo.returnererLagredeVurderinger()

            VurderTiltakspakkerUseCase(ereg, repo).execute(listOf("123"), listOf(tiltakspakkeA))

            coVerify {
                repo.lagreVurderinger(
                    listOf(
                        VurderingForLagring(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                            fylkeskode = "54",
                            vurderingsgrunn = Vurderingsgrunn.UTGATT_FYLKESKODE,
                        ),
                    ),
                )
            }
        }

        test("evaluerer kun nye orgnumre, hopper over eksisterende") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns listOf(
                    Vurderingsresultat("PAKKE_A", "111", Deltakelse.TILTAKSGRUPPE),
                )
            }
            repo.returnererLagredeVurderinger()
            val useCase = VurderTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("111", "222"), tiltakspakker = listOf(tiltakspakkeA))

            coVerify { ereg.hentNoekkelinfo(listOf("222")) }
            coVerify(exactly = 0) { ereg.hentNoekkelinfo(listOf("111")) }
        }

        test("bevarer eksisterende flerpakkeatferd inntil overlapp er avklart") {
            val ereg = mockk<EregClient>()
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns listOf(
                    Vurderingsresultat("PAKKE_A", "111", Deltakelse.TILTAKSGRUPPE),
                )
            }
            repo.returnererLagredeVurderinger()

            VurderTiltakspakkerUseCase(ereg, repo).execute(
                orgnumre = listOf("111"),
                tiltakspakker = listOf(tiltakspakkeA, tiltakspakkeB),
            ) shouldBe listOf(
                TiltakspakkeVurdering(
                    "PAKKE_A",
                    listOf(VirksomhetDeltakelse("111", Deltakelse.TILTAKSGRUPPE)),
                ),
            )

            coVerify(exactly = 0) { ereg.hentNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.lagreVurderinger(any()) }
        }

        test("returnerer første lagrede vurdering når en annen forespørsel vinner kappløpet") {
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("111")) } returns listOf(EregNoekkelinfo("111", adresseI50))
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
                coEvery { lagreVurderinger(any()) } returns listOf(
                    Vurderingsresultat(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "111",
                        deltakelse = Deltakelse.KONTROLLGRUPPE,
                        fylkeskode = "50",
                        vurderingsgrunn = Vurderingsgrunn.FYLKE_I_SCOPE,
                    ),
                )
            }

            VurderTiltakspakkerUseCase(ereg, repo).execute(
                orgnumre = listOf("111"),
                tiltakspakker = listOf(tiltakspakkeA),
            ) shouldBe listOf(
                TiltakspakkeVurdering(
                    "PAKKE_A",
                    listOf(VirksomhetDeltakelse("111", Deltakelse.KONTROLLGRUPPE)),
                ),
            )
        }

        test("håndterer flere tiltakspakker og orgnumre med korrekt gruppering") {
            val adresse = Adresse("Forretningsadresse", "7004", "5001")
            val ereg = mockk<EregClient> {
                coEvery { hentNoekkelinfo(listOf("111", "222")) } returns listOf(
                    EregNoekkelinfo("111", adresse),
                    EregNoekkelinfo("222", adresse),
                )
            }
            val repo = mockk<TiltakspakkeVurderingRepository> {
                coEvery { hentVurderinger(any(), any()) } returns emptyList()
            }
            repo.returnererLagredeVurderinger()
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
                        VurderingForLagring(
                            "PAKKE_A",
                            "111",
                            Deltakelse.TILTAKSGRUPPE,
                            "50",
                            Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                        VurderingForLagring(
                            "PAKKE_B",
                            "111",
                            Deltakelse.TILTAKSGRUPPE,
                            "50",
                            Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                        VurderingForLagring(
                            "PAKKE_A",
                            "222",
                            Deltakelse.TILTAKSGRUPPE,
                            "50",
                            Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                        VurderingForLagring(
                            "PAKKE_B",
                            "222",
                            Deltakelse.TILTAKSGRUPPE,
                            "50",
                            Vurderingsgrunn.FYLKE_I_SCOPE,
                        ),
                    ),
                )
            }
        }
    })

private fun TiltakspakkeVurderingRepository.returnererLagredeVurderinger() {
    coEvery { lagreVurderinger(any()) } answers {
        firstArg<Collection<VurderingForLagring>>().map { vurdering ->
            Vurderingsresultat(
                tiltakspakkeId = vurdering.tiltakspakkeId,
                orgnummer = vurdering.orgnummer,
                deltakelse = vurdering.deltakelse,
                fylkeskode = vurdering.fylkeskode,
                vurderingsgrunn = vurdering.vurderingsgrunn,
            )
        }
    }
}
