package no.nav.flaggskipet.infrastructure.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.Vurderingsgrunn
import java.sql.DriverManager

class VurderingsgrunnlagMigrationTest :
    FunSpec({
        val fixture = PostgresTestFixture()

        afterSpec { fixture.close() }

        test("V8 merker gamle vurderinger, V9 frigjør verdilisten og V10 nullstiller") {
            fixture.migrate(target = "7")
            DriverManager.getConnection(fixture.jdbcUrl, fixture.username, fixture.password).use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO tiltakspakke_deltakelse (tiltakspakke_id, orgnummer, deltakelse)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, "OPPFOLGINGSPLAN_TILTAKSPAKKE_1")
                    statement.setString(2, "123456789")
                    statement.setString(3, Deltakelse.TILTAKSGRUPPE.name)
                    statement.executeUpdate()
                }
            }

            fixture.migrate(target = "8")

            DriverManager.getConnection(fixture.jdbcUrl, fixture.username, fixture.password).use { connection ->
                connection.prepareStatement(
                    """
                    SELECT fylkeskode, vurderingsgrunn
                    FROM tiltakspakke_deltakelse
                    WHERE tiltakspakke_id = ? AND orgnummer = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, "OPPFOLGINGSPLAN_TILTAKSPAKKE_1")
                    statement.setString(2, "123456789")
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getString("fylkeskode") shouldBe null
                        result.getString("vurderingsgrunn") shouldBe Vurderingsgrunn.IKKE_REGISTRERT.name
                    }
                }

                connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'tiltakspakke_deltakelse'
                      AND constraint_name = 'chk_tiltakspakke_vurderingsgrunn'
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getInt(1) shouldBe 1
                    }
                }
            }

            fixture.migrate(target = "9")

            DriverManager.getConnection(fixture.jdbcUrl, fixture.username, fixture.password).use { connection ->
                connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'tiltakspakke_deltakelse'
                      AND constraint_name = 'chk_tiltakspakke_vurderingsgrunn'
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getInt(1) shouldBe 0
                    }
                }

                connection.prepareStatement(
                    """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'tiltakspakke_deltakelse'
                      AND column_name = 'vurderingsgrunn'
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getString("data_type") shouldBe "text"
                    }
                }

                connection.prepareStatement("SELECT COUNT(*) FROM tiltakspakke_deltakelse").use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getInt(1) shouldBe 1
                    }
                }
            }

            fixture.migrate()

            DriverManager.getConnection(fixture.jdbcUrl, fixture.username, fixture.password).use { connection ->
                connection.prepareStatement(
                    """
                    SELECT column_default
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'tiltakspakke_deltakelse'
                      AND column_name = 'vurderingsgrunn'
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getString("column_default") shouldBe null
                    }
                }

                connection.prepareStatement("SELECT COUNT(*) FROM tiltakspakke_deltakelse").use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getInt(1) shouldBe 0
                    }
                }
            }
        }

        test("vurderingsgrunn lagres som fri tekst uten pakke 1-spesifikk verdiliste") {
            fixture.migrate()

            DriverManager.getConnection(fixture.jdbcUrl, fixture.username, fixture.password).use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO tiltakspakke_deltakelse
                        (tiltakspakke_id, orgnummer, deltakelse, vurderingsgrunn)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, "FREMTIDIG_TILTAKSPAKKE")
                    statement.setString(2, "987654321")
                    statement.setString(3, Deltakelse.UTENFOR_SCOPE.name)
                    statement.setString(4, "FREMTIDIG_VURDERINGSGRUNN")
                    statement.executeUpdate() shouldBe 1
                }

                connection.prepareStatement(
                    """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'tiltakspakke_deltakelse'
                      AND column_name = 'vurderingsgrunn'
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() shouldBe true
                        result.getString("data_type") shouldBe "text"
                    }
                }
            }
        }
    })
