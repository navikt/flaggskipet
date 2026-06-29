package no.nav.flaggskipet.domain.vurdering

import kotlinx.datetime.LocalDate

data class GeoTiltakspakkeRegel(
    private val fylkerIScopet: Set<String>,
    private val sannsynlighet: Double = 0.5,
) : Regel {
    override fun vurder(virksomhet: VirksomhetUnderVurdering): Deltakelse = when {
        virksomhet.adresse.fylke() !in fylkerIScopet ->
            Deltakelse.UTENFOR_SCOPE

        erSann(sannsynlighet) ->
            Deltakelse.TILTAKSGRUPPE

        else ->
            Deltakelse.KONTROLLGRUPPE
    }
}

val getGjeldendeTiltakspakker = listOf(
    Tiltakspakke(
        id = "OPPFOLGINGSPLAN_TILTAKSPAKKE_1",
        sluttdato = LocalDate(2026, 7, 1),
        regel = GeoTiltakspakkeRegel(
            fylkerIScopet = setOf("50", "54", "55"),
            sannsynlighet = 0.5,
        ),
    ),
).filter { it.erGjeldene() }
