package no.nav.flaggskipet.domain.vurdering

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VurderingsresultatTest :
    FunSpec({

        test("groupByTiltakspakke returnerer tom liste for tom input") {
            emptyList<Vurderingsresultat>().groupByTiltakspakke() shouldBe emptyList()
        }

        test("groupByTiltakspakke returnerer en enkelt gruppe for enkelt resultat") {
            listOf(
                Vurderingsresultat("PAKKE_A", "123", Deltakelse.TILTAKSGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.TILTAKSGRUPPE))),
            )
        }

        test("groupByTiltakspakke grupperer flere virksomheter under samme tiltakspakke") {
            listOf(
                Vurderingsresultat("PAKKE_A", "123", Deltakelse.TILTAKSGRUPPE),
                Vurderingsresultat("PAKKE_A", "456", Deltakelse.KONTROLLGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeVurdering(
                    "PAKKE_A",
                    listOf(
                        VirksomhetDeltakelse("123", Deltakelse.TILTAKSGRUPPE),
                        VirksomhetDeltakelse("456", Deltakelse.KONTROLLGRUPPE),
                    ),
                ),
            )
        }

        test("groupByTiltakspakke grupperer flere tiltakspakker sortert etter id") {
            listOf(
                Vurderingsresultat("PAKKE_B", "456", Deltakelse.UTENFOR_SCOPE),
                Vurderingsresultat("PAKKE_A", "123", Deltakelse.TILTAKSGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeVurdering("PAKKE_A", listOf(VirksomhetDeltakelse("123", Deltakelse.TILTAKSGRUPPE))),
                TiltakspakkeVurdering("PAKKE_B", listOf(VirksomhetDeltakelse("456", Deltakelse.UTENFOR_SCOPE))),
            )
        }

        test("groupByTiltakspakke sorterer virksomheter etter orgnummer innen hver gruppe") {
            listOf(
                Vurderingsresultat("PAKKE_A", "999", Deltakelse.TILTAKSGRUPPE),
                Vurderingsresultat("PAKKE_A", "111", Deltakelse.KONTROLLGRUPPE),
            ).groupByTiltakspakke() shouldBe listOf(
                TiltakspakkeVurdering(
                    "PAKKE_A",
                    listOf(
                        VirksomhetDeltakelse("111", Deltakelse.KONTROLLGRUPPE),
                        VirksomhetDeltakelse("999", Deltakelse.TILTAKSGRUPPE),
                    ),
                ),
            )
        }
    })
