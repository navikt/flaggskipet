package no.nav.flaggskipet.domain.assessment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ActiveTiltakspakkerTest :
    FunSpec({
        val tiltakspakke = activeTiltakspakker(tiltakspakke2Visible = false).single { it.id == "OPPFOLGINGSPLAN_TILTAKSPAKKE_1" }

        test("Tiltakspakke 2 inherits from Tiltakspakke 1 and is only visible when toggled on") {
            listOf(false, true).forEach { visible ->
                val pakker = activeTiltakspakker(tiltakspakke2Visible = visible)
                pakker.map { it.id } shouldBe listOf("OPPFOLGINGSPLAN_TILTAKSPAKKE_1", "DIALOGMOTE_TILTAKSPAKKE_2")
                pakker.last().inheritsFrom shouldBe tiltakspakke.id
                pakker.last().visible shouldBe visible
            }
        }

        test("Tiltakspakke 2 falls back to the geography of Tiltakspakke 1 with its own key") {
            val pakke2 = activeTiltakspakker(tiltakspakke2Visible = true).last()
            pakke2.rule shouldBe GeoTiltakspakkeRule(setOf("55", "56"), 0.5, pakke2.id)
        }

        test("Tiltakspakke 1 includes virksomheter in Finnmark") {
            tiltakspakke.assess(virksomhetInKommune("5601")) shouldNotBe Participation.UTENFOR_SCOPE
        }

        test("Tiltakspakke 1 includes virksomheter in Troms") {
            tiltakspakke.assess(virksomhetInKommune("5501")) shouldNotBe Participation.UTENFOR_SCOPE
        }

        test("Tiltakspakke 1 excludes virksomheter in Trøndelag") {
            tiltakspakke.assess(virksomhetInKommune("5001")) shouldBe Participation.UTENFOR_SCOPE
        }

        test("Tiltakspakke 1 treats expired fylkeskode 54 as out of scope") {
            tiltakspakke.assess(virksomhetInKommune("5401")) shouldBe Participation.UTENFOR_SCOPE
        }

        test("Tiltakspakke 1 assigns the same virksomhet consistently") {
            val virksomhet = virksomhetInKommune("5501")

            (1..20).map { tiltakspakke.assess(virksomhet) }.distinct().size shouldBe 1
        }

        test("Tiltakspakke 1 locks the assignment algorithm and 50 percent threshold") {
            listOf(
                "123456789",
                "111111111",
                "100701057", // SHA-256-bucket 0.499995018736616
                "100391120", // SHA-256-bucket 0.500004474478196
            ).map { orgnummer ->
                tiltakspakke.assess(virksomhetInKommune("5501", orgnummer))
            } shouldBe listOf(
                Participation.KONTROLLGRUPPE,
                Participation.TILTAKSGRUPPE,
                Participation.TILTAKSGRUPPE,
                Participation.KONTROLLGRUPPE,
            )
        }
    })

private fun virksomhetInKommune(
    kommunenummer: String,
    orgnummer: String = "123456789",
) = VirksomhetUnderAssessment(
    orgnummer = orgnummer,
    adresse = Adresse(
        type = "Forretningsadresse",
        postnummer = "9501",
        kommunenummer = kommunenummer,
    ),
)
