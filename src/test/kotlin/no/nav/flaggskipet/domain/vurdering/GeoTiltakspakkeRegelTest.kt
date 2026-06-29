package no.nav.flaggskipet.domain.vurdering

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GeoTiltakspakkeRegelTest :
    FunSpec({
        val adresseI50 = Adresse(type = "Foretningsadresse", postnummer = "7004", kommunenummer = "5001")
        val adresseI30 = Adresse(type = "Foretningsadresse", postnummer = "0155", kommunenummer = "0301")

        test("returnerer TILTAKSGRUPPE når fylke er i scopet og sannsynligeht = 1.0") {
            val regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 1.0)
            regel.vurder((VirksomhetUnderVurdering(orgnummer = "123456789", adresse = adresseI50))) shouldBe Deltakelse.TILTAKSGRUPPE
        }

        test("returnerer KONTROLLGRUPPE når fylke er i scopet og sannsynglihet = 0.0") {
            val regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 0.0)
            regel.vurder((VirksomhetUnderVurdering(orgnummer = "123456789", adresse = adresseI50))) shouldBe Deltakelse.KONTROLLGRUPPE
        }

        test("returnerer UTENFOR_SCOPE når fylke er ut av scopet") {
            val regel = GeoTiltakspakkeRegel(fylkerIScopet = setOf("50"), sannsynlighet = 0.0)
            regel.vurder((VirksomhetUnderVurdering(orgnummer = "123456789", adresse = adresseI30))) shouldBe Deltakelse.UTENFOR_SCOPE
        }
    })
