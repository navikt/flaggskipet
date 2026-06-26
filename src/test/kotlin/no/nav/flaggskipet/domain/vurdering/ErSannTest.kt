package no.nav.flaggskipet.domain.vurdering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class ErSannTest :
    FunSpec({
        test("throws for porbability below 0.0") {
            shouldThrow<IllegalArgumentException> { erSann(-0.01) }
        }

        test("throws for porbability above 1.0") {
            shouldThrow<IllegalArgumentException> { erSann(1.01) }
        }
    })
