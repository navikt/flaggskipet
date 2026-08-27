package no.nav.flaggskipet.domain.vurdering

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GjeldendeTiltakspakkerTest :
    FunSpec({
        val tiltakspakke = getGjeldendeTiltakspakker.single { it.id == "OPPFOLGINGSPLAN_TILTAKSPAKKE_1" }

        test("tiltakspakke 1 inkluderer virksomheter i Finnmark") {
            tiltakspakke.vurder(virksomhetIKommune("5601")) shouldNotBe Deltakelse.UTENFOR_SCOPE
        }

        test("tiltakspakke 1 inkluderer virksomheter i Troms") {
            tiltakspakke.vurder(virksomhetIKommune("5501")) shouldNotBe Deltakelse.UTENFOR_SCOPE
        }

        test("tiltakspakke 1 ekskluderer virksomheter i Trøndelag") {
            tiltakspakke.vurder(virksomhetIKommune("5001")) shouldBe Deltakelse.UTENFOR_SCOPE
        }

        test("tiltakspakke 1 behandler utgått fylkeskode 54 som utenfor produksjonsscope") {
            tiltakspakke.vurder(virksomhetIKommune("5401")) shouldBe Deltakelse.UTENFOR_SCOPE
        }

        test("tiltakspakke 1 gir samme tildeling ved gjentatt vurdering av samme virksomhet") {
            val virksomhet = virksomhetIKommune("5501")

            (1..20).map { tiltakspakke.vurder(virksomhet) }.distinct().size shouldBe 1
        }

        test("tiltakspakke 1 låser tildelingsalgoritmen og 50 prosent-grensen") {
            listOf(
                "123456789",
                "111111111",
                "100701057", // SHA-256-bucket 0.499995018736616
                "100391120", // SHA-256-bucket 0.500004474478196
            ).map { orgnummer ->
                tiltakspakke.vurder(virksomhetIKommune("5501", orgnummer))
            } shouldBe listOf(
                Deltakelse.KONTROLLGRUPPE,
                Deltakelse.TILTAKSGRUPPE,
                Deltakelse.TILTAKSGRUPPE,
                Deltakelse.KONTROLLGRUPPE,
            )
        }
    })

private fun virksomhetIKommune(
    kommunenummer: String,
    orgnummer: String = "123456789",
) = VirksomhetUnderVurdering(
    orgnummer = orgnummer,
    adresse = Adresse(
        type = "Forretningsadresse",
        postnummer = "9501",
        kommunenummer = kommunenummer,
    ),
)
