package no.nav.flaggskipet.domain.vurdering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class ErSannTest :
    FunSpec({
        test("kaster for sannsynlighet under 0.0") {
            shouldThrow<IllegalArgumentException> { erSann(-0.01) }
        }

        test("kaster for sannsynlighet over 1.0") {
            shouldThrow<IllegalArgumentException> { erSann(1.01) }
        }
    })
