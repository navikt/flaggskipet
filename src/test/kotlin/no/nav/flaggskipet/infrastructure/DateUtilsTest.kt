package no.nav.flaggskipet.infrastructure

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import no.nav.flaggskipet.domain.dagensDato
import kotlin.time.Instant

class DateUtilsTest :
    FunSpec({

        class DateUtilsTest :
            FunSpec({

                val oslo = TimeZone.of("Europe/Oslo")

                test("dagensDato håndterer Oslo day rollover") {
                    dagensDato(
                        Instant.parse("2026-06-23T22:30:00Z"),
                        oslo,
                    ) shouldBe LocalDate(2026, 6, 24)
                }

                test("dagensDato holder samme dato uten rollover") {
                    dagensDato(
                        Instant.parse("2026-01-05T10:15:30Z"),
                        oslo,
                    ) shouldBe LocalDate(2026, 1, 5)
                }
            })
    })
