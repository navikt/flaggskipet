package no.nav.flaggskipet.domain.vurdering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class DeterministiskTrekkTest :
    FunSpec({
        test("kaster for sannsynlighet under 0.0") {
            shouldThrow<IllegalArgumentException> { trekkesTilTiltaksgruppe(-0.01, "test") }
        }

        test("kaster for sannsynlighet over 1.0") {
            shouldThrow<IllegalArgumentException> { trekkesTilTiltaksgruppe(1.01, "test") }
        }
    })
