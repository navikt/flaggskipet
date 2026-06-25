package no.nav.flaggskipet.infrastructure.db.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.infrastructure.db.queryForInt
import no.nav.flaggskipet.infrastructure.db.withMigratedPostgres

class TiltakspakkeVurderingRepositoryTest :
    FunSpec({
        test("lagrer og oppdaterer vurderinger per tiltakspakke og orgnummer") {
            withMigratedPostgres { dataSource, database ->
                val repository = TiltakspakkeVurderingRepositoryImpl(database)

                repository.lagreVurderinger(
                    listOf(
                        NyTiltakspakkeVurdering(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.DELTAR,
                            vurderingsgrunnlag = AdresseVurderingsgrunnlagData(
                                type = "forretningsadresse",
                                adresselinje1 = "Storgata 1",
                                postnummer = "0155",
                                landkode = "NO",
                                kommunenummer = "0301",
                            ),
                        ),
                        NyTiltakspakkeVurdering(
                            tiltakspakkeId = "PAKKE_B",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                            vurderingsgrunnlag = EregIkkeFunnetVurderingsgrunnlagData("123456789"),
                        ),
                    ),
                )
                repository.lagreVurderinger(
                    listOf(
                        NyTiltakspakkeVurdering(
                            tiltakspakkeId = "PAKKE_A",
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.DELTAR_IKKE,
                            vurderingsgrunnlag = AdresseVurderingsgrunnlagData(
                                type = "forretningsadresse",
                                adresselinje1 = "Karl Johans gate 1",
                                postnummer = "0154",
                                landkode = "NO",
                                kommunenummer = "0301",
                            ),
                        ),
                    ),
                )

                repository.hentVurderinger(
                    orgnumre = listOf("123456789"),
                    tiltakspakkeIder = listOf("PAKKE_A", "PAKKE_B"),
                ) shouldBe listOf(
                    TiltakspakkeVurdering(
                        id = "PAKKE_A",
                        virksomheter = listOf(
                            VirksomhetDeltakelse(
                                orgnummer = "123456789",
                                deltakelse = Deltakelse.DELTAR_IKKE,
                            ),
                        ),
                    ),
                    TiltakspakkeVurdering(
                        id = "PAKKE_B",
                        virksomheter = listOf(
                            VirksomhetDeltakelse(
                                orgnummer = "123456789",
                                deltakelse = Deltakelse.UTENFOR_SCOPE,
                            ),
                        ),
                    ),
                )

                dataSource.queryForInt(
                    """
                    SELECT COUNT(*)
                    FROM tiltakspakke_deltakelse
                    """.trimIndent(),
                ) shouldBeExactly 2

                dataSource.connection.use { connection ->
                    connection
                        .prepareStatement(
                            """
                            SELECT vurderingsgrunnlag::text
                            FROM tiltakspakke_deltakelse
                            WHERE tiltakspakke_id = ? AND orgnummer = ?
                            """.trimIndent(),
                        ).use { statement ->
                            statement.setString(1, "PAKKE_A")
                            statement.setString(2, "123456789")

                            statement.executeQuery().use { resultSet ->
                                resultSet.next()
                                Json.parseToJsonElement(resultSet.getString(1)) shouldBe
                                    AdresseVurderingsgrunnlagData(
                                        type = "forretningsadresse",
                                        adresselinje1 = "Karl Johans gate 1",
                                        postnummer = "0154",
                                        landkode = "NO",
                                        kommunenummer = "0301",
                                    ).toJsonObject()
                            }
                        }
                }
            }
        }
    })
