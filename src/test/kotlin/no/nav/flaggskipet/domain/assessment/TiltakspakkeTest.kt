package no.nav.flaggskipet.domain.assessment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import no.nav.flaggskipet.domain.today

class TiltakspakkeTest :
    FunSpec({

        val tiltakspakke = Tiltakspakke(
            id = "MIN_TILTAK",
            endDate = null,
            rule = object : Rule {
                override fun assess(virksomhet: VirksomhetUnderAssessment): Participation = Participation.TILTAKSGRUPPE
            },
        )

        test("Tiltakspakke without endDate is active") {
            tiltakspakke.isActive() shouldBe true
        }

        test("Tiltakspakke with past endDate is inactive") {
            tiltakspakke.copy(endDate = today().minus(DatePeriod(days = 10))).isActive() shouldBe false
        }

        test("Tiltakspakke with future endDate is active") {
            tiltakspakke.copy(endDate = today().plus(DatePeriod(days = 10))).isActive() shouldBe true
        }

        test("Tiltakspakke ending today is active") {
            tiltakspakke.copy(endDate = today()).isActive() shouldBe true
        }
    })
