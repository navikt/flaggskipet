package no.nav.flaggskipet.domain.vurdering

private const val TILTAKSPAKKE_1_ID = "OPPFOLGINGSPLAN_TILTAKSPAKKE_1"
private const val TROMS_FYLKESKODE = "55"
private const val FINNMARK_FYLKESKODE = "56"

data class GeoTiltakspakkeRegel(
    private val fylkerIScopet: Set<String>,
    private val andelTilTiltaksgruppe: Double = 0.5,
    private val tiltakspakkeId: String,
) : Regel {
    override fun vurder(virksomhet: VirksomhetUnderVurdering): Deltakelse = when {
        virksomhet.adresse.fylke() !in fylkerIScopet ->
            Deltakelse.UTENFOR_SCOPE

        fordelesTilTiltaksgruppe(andelTilTiltaksgruppe, "$tiltakspakkeId:${virksomhet.orgnummer}") ->
            Deltakelse.TILTAKSGRUPPE

        else ->
            Deltakelse.KONTROLLGRUPPE
    }
}

val getGjeldendeTiltakspakker = listOf(
    Tiltakspakke(
        id = TILTAKSPAKKE_1_ID,
        sluttdato = null,
        regel = GeoTiltakspakkeRegel(
            fylkerIScopet = setOf(TROMS_FYLKESKODE, FINNMARK_FYLKESKODE),
            andelTilTiltaksgruppe = 0.5,
            tiltakspakkeId = TILTAKSPAKKE_1_ID,
        ),
    ),
).filter { it.erGjeldene() }
