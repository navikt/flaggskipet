package no.nav.flaggskipet.domain.assessment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GeoTiltakspakkeRuleTest :
    FunSpec({
        val adresseI50 = Adresse(type = "Foretningsadresse", postnummer = "7004", kommunenummer = "5001")
        val adresseI30 = Adresse(type = "Foretningsadresse", postnummer = "0155", kommunenummer = "0301")

        test("returns TILTAKSGRUPPE when fylke is in scope and Tiltaksgruppe share is 1.0") {
            val rule = GeoTiltakspakkeRule(fylkerInScope = setOf("50"), tiltaksgruppeShare = 1.0, tiltakspakkeId = "test")
            rule.assess((VirksomhetUnderAssessment(orgnummer = "123456789", adresse = adresseI50))) shouldBe Participation.TILTAKSGRUPPE
        }

        test("returns KONTROLLGRUPPE when fylke is in scope and Tiltaksgruppe share is 0.0") {
            val rule = GeoTiltakspakkeRule(fylkerInScope = setOf("50"), tiltaksgruppeShare = 0.0, tiltakspakkeId = "test")
            rule.assess((VirksomhetUnderAssessment(orgnummer = "123456789", adresse = adresseI50))) shouldBe Participation.KONTROLLGRUPPE
        }

        test("returns UTENFOR_SCOPE when fylke is out of scope") {
            val rule = GeoTiltakspakkeRule(fylkerInScope = setOf("50"), tiltaksgruppeShare = 0.0, tiltakspakkeId = "test")
            rule.assess((VirksomhetUnderAssessment(orgnummer = "123456789", adresse = adresseI30))) shouldBe Participation.UTENFOR_SCOPE
        }
    })
