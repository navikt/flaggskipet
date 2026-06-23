package no.nav.flaggskipet.infrastructure

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class DateUtilsTest :
    FunSpec({
        test("dagensDatoOslo uses Europe Oslo date") {
            dagensDato(Instant.parse("2026-06-23T22:30:00Z")) shouldBe "2026-06-24"
        }

        test("dagensDatoOslo keeps yyyy-MM-dd format from LocalDate") {
            dagensDato(Instant.parse("2026-01-05T10:15:30Z")) shouldBe "2026-01-05"
        }
    })
