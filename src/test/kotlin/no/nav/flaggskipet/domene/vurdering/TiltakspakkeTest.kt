package no.nav.flaggskipet.domene.vurdering

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Regel
import no.nav.flaggskipet.domain.vurdering.Tiltakspakke
import no.nav.flaggskipet.domain.vurdering.VirksomhetUnderVurdering
import no.nav.flaggskipet.infrastructure.dagensDato

class TiltakspakkeTest :
    FunSpec({

        val tiltakspakke = Tiltakspakke(
            id = "MIN_TILTAK",
            sluttdato = null,
            regel = object : Regel {
                override fun vurder(virksomhet: VirksomhetUnderVurdering): Deltakelse = Deltakelse.TILTAKSGRUPPE
            },
        )

        test("pakke uten sluttdato er gjeldende") {
            tiltakspakke.erGjeldene() shouldBe true
        }

        test("pakke med passert sluttdato er ikke gjeldende") {
            tiltakspakke.copy(sluttdato = dagensDato().minus(DatePeriod(days = 10))).erGjeldene() shouldBe false
        }

        test("pakke med fremtidig sluttdato er gjeldende") {
            tiltakspakke.copy(sluttdato = dagensDato().plus(DatePeriod(days = 10))).erGjeldene() shouldBe true
        }
    })
