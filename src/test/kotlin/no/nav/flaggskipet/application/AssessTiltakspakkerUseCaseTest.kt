package no.nav.flaggskipet.application

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.flaggskipet.application.port.EregClient
import no.nav.flaggskipet.application.port.EregNoekkelinfo
import no.nav.flaggskipet.application.port.NewAssessment
import no.nav.flaggskipet.application.port.TiltakspakkeAssessmentRepository
import no.nav.flaggskipet.domain.assessment.Adresse
import no.nav.flaggskipet.domain.assessment.AssessmentReason
import no.nav.flaggskipet.domain.assessment.AssessmentResult
import no.nav.flaggskipet.domain.assessment.GeoTiltakspakkeRule
import no.nav.flaggskipet.domain.assessment.Participation
import no.nav.flaggskipet.domain.assessment.Tiltakspakke
import no.nav.flaggskipet.domain.assessment.TiltakspakkeAssessment
import no.nav.flaggskipet.domain.assessment.VirksomhetParticipation

class AssessTiltakspakkerUseCaseTest :
    FunSpec({

        val adresseI50 = Adresse(type = "Forretningsadresse", postnummer = "7004", kommunenummer = "5001")
        val tiltakspakkeA = Tiltakspakke(
            id = "PAKKE_A",
            rule = GeoTiltakspakkeRule(fylkerInScope = setOf("50"), tiltaksgruppeShare = 1.0, tiltakspakkeId = "PAKKE_A"),
        )
        val tiltakspakkeB = Tiltakspakke(
            id = "PAKKE_B",
            rule = GeoTiltakspakkeRule(fylkerInScope = setOf("50"), tiltaksgruppeShare = 1.0, tiltakspakkeId = "PAKKE_B"),
        )

        test("returns an empty list when no tiltakspakker exist") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeAssessmentRepository>(relaxed = true)
            val useCase = AssessTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = emptyList()).shouldBeEmpty()

            coVerify(exactly = 0) { ereg.getNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.getAssessments(any(), any()) }
            coVerify(exactly = 0) { repo.saveAssessments(any()) }
        }

        test("returns existing assessments without calling Ereg") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns listOf(
                    AssessmentResult("PAKKE_A", "123", Participation.TILTAKSGRUPPE),
                )
            }
            val useCase = AssessTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.TILTAKSGRUPPE))),
            )

            coVerify(exactly = 0) { ereg.getNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.saveAssessments(any()) }
        }

        test("assesses new orgnumre and saves assessments") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", adresseI50),
                )
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()
            val useCase = AssessTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.TILTAKSGRUPPE))),
            )

            coVerify { ereg.getNoekkelinfo(listOf("123")) }
            coVerify {
                repo.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            participation = Participation.TILTAKSGRUPPE,
                            fylkeskode = "50",
                            assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                        ),
                    ),
                )
            }
        }

        test("assigns UTENFOR_SCOPE when Ereg returns no Adresse") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", null),
                )
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()
            val useCase = AssessTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.UTENFOR_SCOPE))),
            )

            coVerify {
                repo.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            participation = Participation.UTENFOR_SCOPE,
                            fylkeskode = null,
                            assessmentReason = AssessmentReason.MANGLER_ADRESSE,
                        ),
                    ),
                )
            }
        }

        test("assigns UTENFOR_SCOPE when Adresse lacks a valid kommunenummer") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo(
                        "123",
                        Adresse(type = "Forretningsadresse", postnummer = "", kommunenummer = ""),
                    ),
                )
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()
            val useCase = AssessTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("123"), tiltakspakker = listOf(tiltakspakkeA)) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.UTENFOR_SCOPE))),
            )

            coVerify {
                repo.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            participation = Participation.UTENFOR_SCOPE,
                            fylkeskode = null,
                            assessmentReason = AssessmentReason.UGYLDIG_KOMMUNENUMMER,
                        ),
                    ),
                )
            }
        }

        test("saves fylke as reason when virksomhet is outside geographic scope") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", Adresse("Forretningsadresse", "9008", "5501")),
                )
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo).execute(listOf("123"), listOf(tiltakspakkeA))

            coVerify {
                repo.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            participation = Participation.UTENFOR_SCOPE,
                            fylkeskode = "55",
                            assessmentReason = AssessmentReason.FYLKE_UTENFOR_SCOPE,
                        ),
                    ),
                )
            }
        }

        test("marks expired fylkeskode 54 explicitly") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("123")) } returns listOf(
                    EregNoekkelinfo("123", Adresse("Forretningsadresse", "9008", "5401")),
                )
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo).execute(listOf("123"), listOf(tiltakspakkeA))

            coVerify {
                repo.saveAssessments(
                    listOf(
                        NewAssessment(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123",
                            participation = Participation.UTENFOR_SCOPE,
                            fylkeskode = "54",
                            assessmentReason = AssessmentReason.UTGATT_FYLKESKODE,
                        ),
                    ),
                )
            }
        }

        test("assesses only new orgnumre and skips existing assessments") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns listOf(
                    AssessmentResult("PAKKE_A", "111", Participation.TILTAKSGRUPPE),
                )
            }
            repo.returnsSavedAssessments()
            val useCase = AssessTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("111", "222"), tiltakspakker = listOf(tiltakspakkeA))

            coVerify { ereg.getNoekkelinfo(listOf("222")) }
            coVerify(exactly = 0) { ereg.getNoekkelinfo(listOf("111")) }
        }

        test("preserves existing multiple-Tiltakspakke behavior for overlaps") {
            val ereg = mockk<EregClient>()
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns listOf(
                    AssessmentResult("PAKKE_A", "111", Participation.TILTAKSGRUPPE),
                )
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo).execute(
                orgnumre = listOf("111"),
                tiltakspakker = listOf(tiltakspakkeA, tiltakspakkeB),
            ) shouldBe listOf(
                TiltakspakkeAssessment(
                    "PAKKE_A",
                    listOf(VirksomhetParticipation("111", Participation.TILTAKSGRUPPE)),
                ),
            )

            coVerify(exactly = 0) { ereg.getNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.saveAssessments(any()) }
        }

        test("returns the first saved assessment when another request wins the race") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111")) } returns listOf(EregNoekkelinfo("111", adresseI50))
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
                coEvery { saveAssessments(any()) } returns listOf(
                    AssessmentResult(
                        tiltakspakkeId = "PAKKE_A",
                        orgnummer = "111",
                        participation = Participation.KONTROLLGRUPPE,
                        fylkeskode = "50",
                        assessmentReason = AssessmentReason.FYLKE_I_SCOPE,
                    ),
                )
            }

            AssessTiltakspakkerUseCase(ereg, repo).execute(
                orgnumre = listOf("111"),
                tiltakspakker = listOf(tiltakspakkeA),
            ) shouldBe listOf(
                TiltakspakkeAssessment(
                    "PAKKE_A",
                    listOf(VirksomhetParticipation("111", Participation.KONTROLLGRUPPE)),
                ),
            )
        }

        test("groups multiple tiltakspakker and orgnumre correctly") {
            val adresse = Adresse("Forretningsadresse", "7004", "5001")
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111", "222")) } returns listOf(
                    EregNoekkelinfo("111", adresse),
                    EregNoekkelinfo("222", adresse),
                )
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()
            val useCase = AssessTiltakspakkerUseCase(ereg, repo)

            useCase.execute(listOf("111", "222"), tiltakspakker = listOf(tiltakspakkeA, tiltakspakkeB)) shouldBe listOf(
                TiltakspakkeAssessment(
                    "PAKKE_A",
                    listOf(
                        VirksomhetParticipation("111", Participation.TILTAKSGRUPPE),
                        VirksomhetParticipation("222", Participation.TILTAKSGRUPPE),
                    ),
                ),
                TiltakspakkeAssessment(
                    "PAKKE_B",
                    listOf(
                        VirksomhetParticipation("111", Participation.TILTAKSGRUPPE),
                        VirksomhetParticipation("222", Participation.TILTAKSGRUPPE),
                    ),
                ),
            )

            coVerify {
                repo.saveAssessments(
                    listOf(
                        NewAssessment(
                            "PAKKE_A",
                            "111",
                            Participation.TILTAKSGRUPPE,
                            "50",
                            AssessmentReason.FYLKE_I_SCOPE,
                        ),
                        NewAssessment(
                            "PAKKE_B",
                            "111",
                            Participation.TILTAKSGRUPPE,
                            "50",
                            AssessmentReason.FYLKE_I_SCOPE,
                        ),
                        NewAssessment(
                            "PAKKE_A",
                            "222",
                            Participation.TILTAKSGRUPPE,
                            "50",
                            AssessmentReason.FYLKE_I_SCOPE,
                        ),
                        NewAssessment(
                            "PAKKE_B",
                            "222",
                            Participation.TILTAKSGRUPPE,
                            "50",
                            AssessmentReason.FYLKE_I_SCOPE,
                        ),
                    ),
                )
            }
        }
    })

private fun TiltakspakkeAssessmentRepository.returnsSavedAssessments() {
    coEvery { saveAssessments(any()) } answers {
        firstArg<Collection<NewAssessment>>().map { assessment ->
            AssessmentResult(
                tiltakspakkeId = assessment.tiltakspakkeId,
                orgnummer = assessment.orgnummer,
                participation = assessment.participation,
                fylkeskode = assessment.fylkeskode,
                assessmentReason = assessment.assessmentReason,
            )
        }
    }
}
