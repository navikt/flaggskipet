package no.nav.flaggskipet.infrastructure.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import kotlinx.datetime.LocalDate
import no.nav.flaggskipet.domain.assessment.activeTiltakspakker
import no.nav.flaggskipet.infrastructure.database.config.transact
import no.nav.flaggskipet.infrastructure.database.tables.TiltakspakkeTable
import org.jetbrains.exposed.v1.jdbc.selectAll

class TiltakspakkeMetadataTest :
    FunSpec({
        val fixture = PostgresTestFixture()

        beforeSpec { fixture.migrate() }
        afterSpec { fixture.close() }

        test("migrated tiltakspakke metadata matches the Tiltakspakker defined in code") {
            val allTiltakspakker = activeTiltakspakker(tiltakspakke2Visible = true, now = LocalDate(2000, 1, 1))

            fixture.database.transact {
                TiltakspakkeTable.selectAll().map {
                    Triple(it[TiltakspakkeTable.id], it[TiltakspakkeTable.endDate], it[TiltakspakkeTable.inheritsFrom])
                }
            } shouldContainExactlyInAnyOrder allTiltakspakker.map { Triple(it.id, it.endDate, it.inheritsFrom) }
        }
    })
