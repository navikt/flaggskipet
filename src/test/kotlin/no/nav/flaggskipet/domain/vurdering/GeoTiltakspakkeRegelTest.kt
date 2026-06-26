package no.nav.flaggskipet.domain.vurdering

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GeoTiltakspakkeRegelTest :
    FunSpec({
        val adresseI50 = Adresse(type = "Foretningsadresse", postnummer = "7004", kommunenummer = "5001")
        val adresseI30 = Adresse(type = "Foretningsadresse", postnummer = "0155", kommunenummer = "0301")

        test("returns TILTAKSGRUPPE when fylke is in scope and sannsynglihet = 1.0") {
            val regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 1.0)
            regel.vurder((VirksomhetUnderVurdering(orgnummer = "123456789", adresse = adresseI50))) shouldBe Deltakelse.TILTAKSGRUPPE
        }

        test("returns KONTROLLGRUPPE when fylke is in scope and sannsynglihet = 0.0") {
            val regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 0.0)
            regel.vurder((VirksomhetUnderVurdering(orgnummer = "123456789", adresse = adresseI50))) shouldBe Deltakelse.KONTROLLGRUPPE
        }

        test("returns UTENFOR_SCOPE when fylke is out of scope") {
            val regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 0.0)
            regel.vurder((VirksomhetUnderVurdering(orgnummer = "123456789", adresse = adresseI30))) shouldBe Deltakelse.UTENFOR_SCOPE
        }
    })
