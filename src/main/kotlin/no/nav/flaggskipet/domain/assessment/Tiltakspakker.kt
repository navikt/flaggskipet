package no.nav.flaggskipet.domain.assessment

private const val TILTAKSPAKKE_1_ID = "OPPFOLGINGSPLAN_TILTAKSPAKKE_1"
private const val TROMS_FYLKESKODE = "55"
private const val FINNMARK_FYLKESKODE = "56"

data class GeoTiltakspakkeRule(
    private val fylkerInScope: Set<String>,
    private val tiltaksgruppeShare: Double = 0.5,
    private val tiltakspakkeId: String,
) : Rule {
    override fun assess(virksomhet: VirksomhetUnderAssessment): Participation = when {
        virksomhet.adresse.fylke() !in fylkerInScope ->
            Participation.UTENFOR_SCOPE

        isAssignedToTiltaksgruppe(tiltaksgruppeShare, "$tiltakspakkeId:${virksomhet.orgnummer}") ->
            Participation.TILTAKSGRUPPE

        else ->
            Participation.KONTROLLGRUPPE
    }
}

val activeTiltakspakker = listOf(
    Tiltakspakke(
        id = TILTAKSPAKKE_1_ID,
        endDate = null,
        rule = GeoTiltakspakkeRule(
            fylkerInScope = setOf(TROMS_FYLKESKODE, FINNMARK_FYLKESKODE),
            tiltaksgruppeShare = 0.5,
            tiltakspakkeId = TILTAKSPAKKE_1_ID,
        ),
    ),
).filter { it.isActive() }
