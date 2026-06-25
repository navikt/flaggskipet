package no.nav.flaggskipet.domene.vurdering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.flaggskipet.domain.vurdering.Deltakelse
import no.nav.flaggskipet.domain.vurdering.VurderTiltakspakkerUseCase
import no.nav.flaggskipet.domain.vurdering.mergeVurderinger
import no.nav.flaggskipet.infrastructure.clients.ereg.EregClient
import no.nav.flaggskipet.infrastructure.clients.ereg.EregResult
import no.nav.flaggskipet.infrastructure.clients.ereg.Organisasjon
import no.nav.flaggskipet.infrastructure.db.repositories.NyTiltakspakkeVurdering
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurdering
import no.nav.flaggskipet.infrastructure.db.repositories.TiltakspakkeVurderingRepository
import no.nav.flaggskipet.infrastructure.db.repositories.VirksomhetDeltakelse

class VurderTiltakspakkerUseCaseTest :
    FunSpec({
        test("returnerer eksisterende vurderinger uten å kalle ereg når alle orgnumre finnes") {
            val repository = FakeTiltakspakkeVurderingRepository(
                initialState = listOf(
                    NyTiltakspakkeVurdering(
                        tiltakspakkeId = "TILTAKSPAKKE_EN",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.DELTAR,
                    ),
                ),
            )
            val eregClient = FakeEregClient()

            val result = VurderTiltakspakkerUseCase(eregClient, repository).execute(listOf("123456789"))

            result shouldBe listOf(
                TiltakspakkeVurdering(
                    id = "TILTAKSPAKKE_EN",
                    virksomheter = listOf(
                        VirksomhetDeltakelse(
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.DELTAR,
                        ),
                    ),
                ),
            )
            eregClient.requests shouldBe emptyList()
            repository.lagredeVurderinger shouldBe emptyList()
            repository.hentVurderingerKall shouldBe 1
        }

        test("vurderer manglende orgnumre, persisterer og returnerer samlet resultat") {
            val repository = FakeTiltakspakkeVurderingRepository(
                initialState = listOf(
                    NyTiltakspakkeVurdering(
                        tiltakspakkeId = "TILTAKSPAKKE_EN",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.DELTAR,
                    ),
                ),
            )
            val eregClient = FakeEregClient(
                results = mapOf(
                    "987654321" to EregResult.Funnet(
                        organisasjonsnummer = "987654321",
                        organisasjon = Organisasjon(
                            adresse = Organisasjon.Adresse(
                                type = "forretningsadresse",
                                adresselinje1 = "Storgata 1",
                                postnummer = "0155",
                                landkode = "NO",
                                kommunenummer = "0301",
                            ),
                        ),
                    ),
                ),
            )

            val result = VurderTiltakspakkerUseCase(eregClient, repository).execute(listOf("123456789", "987654321"))

            result shouldBe listOf(
                TiltakspakkeVurdering(
                    id = "TILTAKSPAKKE_EN",
                    virksomheter = listOf(
                        VirksomhetDeltakelse(
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.DELTAR,
                        ),
                        VirksomhetDeltakelse(
                            orgnummer = "987654321",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                        ),
                    ),
                ),
            )
            eregClient.requests shouldBe listOf(listOf("987654321"))
            repository.lagredeVurderinger shouldBe listOf(
                NyTiltakspakkeVurdering(
                    tiltakspakkeId = "TILTAKSPAKKE_EN",
                    orgnummer = "987654321",
                    deltakelse = Deltakelse.UTENFOR_SCOPE,
                ),
            )
            repository.hentVurderingerKall shouldBe 1
        }

        test("merge lar nye vurderinger vinne ved konflikt") {
            mergeVurderinger(
                eksisterendeVurderinger = listOf(
                    TiltakspakkeVurdering(
                        id = "TILTAKSPAKKE_EN",
                        virksomheter = listOf(
                            VirksomhetDeltakelse(
                                orgnummer = "123456789",
                                deltakelse = Deltakelse.DELTAR,
                            ),
                        ),
                    ),
                ),
                nyeVurderinger = listOf(
                    NyTiltakspakkeVurdering(
                        tiltakspakkeId = "TILTAKSPAKKE_EN",
                        orgnummer = "123456789",
                        deltakelse = Deltakelse.UTENFOR_SCOPE,
                    ),
                ),
            ) shouldBe listOf(
                TiltakspakkeVurdering(
                    id = "TILTAKSPAKKE_EN",
                    virksomheter = listOf(
                        VirksomhetDeltakelse(
                            orgnummer = "123456789",
                            deltakelse = Deltakelse.UTENFOR_SCOPE,
                        ),
                    ),
                ),
            )
        }

        test("feiler eksplisitt når ereg ikke finner nøkkelinfo") {
            val repository = FakeTiltakspakkeVurderingRepository()
            val eregClient = FakeEregClient(
                results = mapOf(
                    "987654321" to EregResult.IkkeFunnet("987654321"),
                ),
            )

            val error = shouldThrow<IllegalStateException> {
                VurderTiltakspakkerUseCase(eregClient, repository).execute(listOf("987654321"))
            }

            error.message shouldBe "Fant ikke nøkkelinfo for orgnummer 987654321"
            repository.lagredeVurderinger shouldBe emptyList()
        }
    })

private class FakeEregClient(
    private val results: Map<String, EregResult> = emptyMap(),
) : EregClient {
    val requests = mutableListOf<List<String>>()

    override suspend fun hentNoekkelinfo(organisasjonsnummer: List<String>): List<EregResult> {
        requests += organisasjonsnummer
        return organisasjonsnummer.map { orgnummer ->
            results.getValue(orgnummer)
        }
    }
}

private class FakeTiltakspakkeVurderingRepository(
    initialState: List<NyTiltakspakkeVurdering> = emptyList(),
) : TiltakspakkeVurderingRepository {
    private val state = linkedMapOf<Pair<String, String>, NyTiltakspakkeVurdering>()
    val lagredeVurderinger = mutableListOf<NyTiltakspakkeVurdering>()
    var hentVurderingerKall = 0
        private set

    init {
        initialState.forEach { vurdering ->
            state[vurdering.tiltakspakkeId to vurdering.orgnummer] = vurdering
        }
    }

    override suspend fun hentVurderinger(
        orgnumre: Collection<String>,
        tiltakspakkeIder: Collection<String>,
    ): List<TiltakspakkeVurdering> {
        hentVurderingerKall += 1
        return state.values
            .filter { it.orgnummer in orgnumre && it.tiltakspakkeId in tiltakspakkeIder }
            .groupBy(NyTiltakspakkeVurdering::tiltakspakkeId)
            .toSortedMap()
            .map { (tiltakspakkeId, vurderinger) ->
                TiltakspakkeVurdering(
                    id = tiltakspakkeId,
                    virksomheter = vurderinger
                        .sortedBy(NyTiltakspakkeVurdering::orgnummer)
                        .map { vurdering ->
                            VirksomhetDeltakelse(
                                orgnummer = vurdering.orgnummer,
                                deltakelse = vurdering.deltakelse,
                            )
                        },
                )
            }
    }

    override suspend fun lagreVurderinger(vurderinger: Collection<NyTiltakspakkeVurdering>) {
        vurderinger.forEach { vurdering ->
            lagredeVurderinger += vurdering
            state[vurdering.tiltakspakkeId to vurdering.orgnummer] = vurdering
        }
    }
}
