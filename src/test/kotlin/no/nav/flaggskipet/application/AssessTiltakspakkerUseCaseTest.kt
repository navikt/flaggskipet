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

        // Inherits from PAKKE_A, but its own rule always gives KONTROLLGRUPPE, so inherited and fallback results differ.
        val arvendePakke = Tiltakspakke(
            id = "PAKKE_ARV",
            rule = GeoTiltakspakkeRule(fylkerInScope = setOf("50"), tiltaksgruppeShare = 0.0, tiltakspakkeId = "PAKKE_ARV"),
            inheritsFrom = "PAKKE_A",
        )

        test("returns an empty list when no tiltakspakker exist") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeAssessmentRepository>(relaxed = true)

            AssessTiltakspakkerUseCase(ereg, repo, emptyList()).execute(listOf("123")).shouldBeEmpty()

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

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA)).execute(listOf("123")) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.TILTAKSGRUPPE))),
            )

            coVerify(exactly = 0) { ereg.getNoekkelinfo(any()) }
            coVerify(exactly = 0) { repo.saveAssessments(any()) }
        }

        test("assesses new orgnumre and saves assessments") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("123")) } returns listOf(EregNoekkelinfo("123", adresseI50))
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA)).execute(listOf("123")) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.TILTAKSGRUPPE))),
            )

            coVerify {
                repo.saveAssessments(
                    listOf(NewAssessment("PAKKE_A", "123", Participation.TILTAKSGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE)),
                )
            }
        }

        listOf(
            Triple("no Adresse", null, NewAssessment("PAKKE_A", "123", Participation.UTENFOR_SCOPE, null, AssessmentReason.MANGLER_ADRESSE)),
            Triple(
                "an invalid kommunenummer",
                Adresse("Forretningsadresse", "", ""),
                NewAssessment("PAKKE_A", "123", Participation.UTENFOR_SCOPE, null, AssessmentReason.UGYLDIG_KOMMUNENUMMER),
            ),
            Triple(
                "a fylke outside scope",
                Adresse("Forretningsadresse", "9008", "5501"),
                NewAssessment("PAKKE_A", "123", Participation.UTENFOR_SCOPE, "55", AssessmentReason.FYLKE_UTENFOR_SCOPE),
            ),
            Triple(
                "the expired fylkeskode 54",
                Adresse("Forretningsadresse", "9008", "5401"),
                NewAssessment("PAKKE_A", "123", Participation.UTENFOR_SCOPE, "54", AssessmentReason.UTGATT_FYLKESKODE),
            ),
        ).forEach { (description, adresse, expected) ->
            test("saves UTENFOR_SCOPE with its reason for $description") {
                val ereg = mockk<EregClient> {
                    coEvery { getNoekkelinfo(listOf("123")) } returns listOf(EregNoekkelinfo("123", adresse))
                }
                val repo = mockk<TiltakspakkeAssessmentRepository> {
                    coEvery { getAssessments(any(), any()) } returns emptyList()
                }
                repo.returnsSavedAssessments()

                AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA)).execute(listOf("123"))

                coVerify { repo.saveAssessments(listOf(expected)) }
            }
        }

        test("assesses only orgnumre without an assessment") {
            val ereg = mockk<EregClient>(relaxed = true)
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns listOf(
                    AssessmentResult("PAKKE_A", "111", Participation.TILTAKSGRUPPE),
                )
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA)).execute(listOf("111", "222"))

            coVerify(exactly = 1) { ereg.getNoekkelinfo(listOf("222")) }
        }

        test("assesses a tiltakspakke even when the orgnummer is assessed in another tiltakspakke") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111")) } returns listOf(EregNoekkelinfo("111", adresseI50))
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns listOf(
                    AssessmentResult("PAKKE_A", "111", Participation.TILTAKSGRUPPE),
                )
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA, tiltakspakkeB)).execute(listOf("111"))

            coVerify(exactly = 1) {
                repo.saveAssessments(
                    listOf(NewAssessment("PAKKE_B", "111", Participation.TILTAKSGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE)),
                )
            }
        }

        test("returns the first saved assessment when another request wins the race") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111")) } returns listOf(EregNoekkelinfo("111", adresseI50))
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
                coEvery { saveAssessments(any()) } returns listOf(
                    AssessmentResult("PAKKE_A", "111", Participation.KONTROLLGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE),
                )
            }

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA)).execute(listOf("111")) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("111", Participation.KONTROLLGRUPPE))),
            )
        }

        test("groups multiple tiltakspakker and orgnumre with one Ereg call") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111", "222")) } returns listOf(
                    EregNoekkelinfo("111", adresseI50),
                    EregNoekkelinfo("222", adresseI50),
                )
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA, tiltakspakkeB))
                .execute(listOf("111", "222")) shouldBe listOf("PAKKE_A", "PAKKE_B").map { id ->
                TiltakspakkeAssessment(
                    id,
                    listOf(
                        VirksomhetParticipation("111", Participation.TILTAKSGRUPPE),
                        VirksomhetParticipation("222", Participation.TILTAKSGRUPPE),
                    ),
                )
            }

            coVerify(exactly = 1) { ereg.getNoekkelinfo(any()) }
        }

        test("inherits stored assessments including reason and fylkeskode without calling Ereg") {
            val ereg = mockk<EregClient>()
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(listOf("111", "222"), listOf("PAKKE_ARV", "PAKKE_A")) } returns listOf(
                    AssessmentResult("PAKKE_A", "111", Participation.TILTAKSGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE),
                    AssessmentResult("PAKKE_A", "222", Participation.UTENFOR_SCOPE, null, AssessmentReason.MANGLER_ADRESSE),
                )
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(arvendePakke)).execute(listOf("111", "222")) shouldBe listOf(
                TiltakspakkeAssessment(
                    "PAKKE_ARV",
                    listOf(
                        VirksomhetParticipation("111", Participation.TILTAKSGRUPPE),
                        VirksomhetParticipation("222", Participation.UTENFOR_SCOPE),
                    ),
                ),
            )

            coVerify {
                repo.saveAssessments(
                    listOf(
                        NewAssessment("PAKKE_ARV", "111", Participation.TILTAKSGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE),
                        NewAssessment("PAKKE_ARV", "222", Participation.UTENFOR_SCOPE, null, AssessmentReason.MANGLER_ADRESSE),
                    ),
                )
            }
            coVerify(exactly = 0) { ereg.getNoekkelinfo(any()) }
        }

        test("inherits the assessment saved earlier in the same call") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111")) } returns listOf(EregNoekkelinfo("111", adresseI50))
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA, arvendePakke)).execute(listOf("111"))

            coVerify {
                repo.saveAssessments(
                    listOf(NewAssessment("PAKKE_ARV", "111", Participation.TILTAKSGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE)),
                )
            }
            coVerify(exactly = 1) { ereg.getNoekkelinfo(any()) }
        }

        test("assesses with its own rule when there is nothing to inherit") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111")) } returns listOf(EregNoekkelinfo("111", adresseI50))
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(arvendePakke)).execute(listOf("111"))

            coVerify {
                repo.saveAssessments(
                    listOf(NewAssessment("PAKKE_ARV", "111", Participation.KONTROLLGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE)),
                )
            }
        }

        test("assesses and saves a hidden tiltakspakke without returning it") {
            val ereg = mockk<EregClient> {
                coEvery { getNoekkelinfo(listOf("111")) } returns listOf(EregNoekkelinfo("111", adresseI50))
            }
            val repo = mockk<TiltakspakkeAssessmentRepository> {
                coEvery { getAssessments(any(), any()) } returns emptyList()
            }
            repo.returnsSavedAssessments()

            AssessTiltakspakkerUseCase(ereg, repo, listOf(tiltakspakkeA, tiltakspakkeB.copy(visible = false)))
                .execute(listOf("111")) shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("111", Participation.TILTAKSGRUPPE))),
            )

            coVerify {
                repo.saveAssessments(
                    listOf(NewAssessment("PAKKE_B", "111", Participation.TILTAKSGRUPPE, "50", AssessmentReason.FYLKE_I_SCOPE)),
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
