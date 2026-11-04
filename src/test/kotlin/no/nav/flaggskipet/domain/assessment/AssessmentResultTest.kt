package no.nav.flaggskipet.domain.assessment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AssessmentResultTest :
    FunSpec({

        test("groupByTiltakspakke returns an empty list for empty input") {
            emptyList<AssessmentResult>().groupByTiltakspakke() shouldBe emptyList()
        }

        test("groupByTiltakspakke returns one group for one result") {
            listOf(
                AssessmentResult("PAKKE_A", "123", Participation.TILTAKSGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.TILTAKSGRUPPE))),
            )
        }

        test("groupByTiltakspakke groups multiple virksomheter under one Tiltakspakke") {
            listOf(
                AssessmentResult("PAKKE_A", "123", Participation.TILTAKSGRUPPE),
                AssessmentResult("PAKKE_A", "456", Participation.KONTROLLGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeAssessment(
                    "PAKKE_A",
                    listOf(
                        VirksomhetParticipation("123", Participation.TILTAKSGRUPPE),
                        VirksomhetParticipation("456", Participation.KONTROLLGRUPPE),
                    ),
                ),
            )
        }

        test("groupByTiltakspakke sorts multiple tiltakspakker by id") {
            listOf(
                AssessmentResult("PAKKE_B", "456", Participation.UTENFOR_SCOPE),
                AssessmentResult("PAKKE_A", "123", Participation.TILTAKSGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeAssessment("PAKKE_A", listOf(VirksomhetParticipation("123", Participation.TILTAKSGRUPPE))),
                TiltakspakkeAssessment("PAKKE_B", listOf(VirksomhetParticipation("456", Participation.UTENFOR_SCOPE))),
            )
        }

        test("groupByTiltakspakke sorts virksomheter by orgnummer within each group") {
            listOf(
                AssessmentResult("PAKKE_A", "999", Participation.TILTAKSGRUPPE),
                AssessmentResult("PAKKE_A", "111", Participation.KONTROLLGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeAssessment(
                    "PAKKE_A",
                    listOf(
                        VirksomhetParticipation("111", Participation.KONTROLLGRUPPE),
                        VirksomhetParticipation("999", Participation.TILTAKSGRUPPE),
                    ),
                ),
            )
        }
    })
