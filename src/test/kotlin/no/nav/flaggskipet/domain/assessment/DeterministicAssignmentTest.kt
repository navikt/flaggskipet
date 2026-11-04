package no.nav.flaggskipet.domain.assessment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class DeterministicAssignmentTest :
    FunSpec({
        test("rejects Tiltaksgruppe share below 0.0") {
            shouldThrow<IllegalArgumentException> { isAssignedToTiltaksgruppe(-0.01, "test") }
        }

        test("rejects Tiltaksgruppe share above 1.0") {
            shouldThrow<IllegalArgumentException> { isAssignedToTiltaksgruppe(1.01, "test") }
        }
    })
