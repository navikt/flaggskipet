package no.nav.flaggskipet.domain.vurdering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class DeterministiskTrekkTest :
    FunSpec({
        test("kaster for andel til tiltaksgruppe under 0.0") {
            shouldThrow<IllegalArgumentException> { fordelesTilTiltaksgruppe(-0.01, "test") }
        }

        test("kaster for andel til tiltaksgruppe over 1.0") {
            shouldThrow<IllegalArgumentException> { fordelesTilTiltaksgruppe(1.01, "test") }
        }
    })
