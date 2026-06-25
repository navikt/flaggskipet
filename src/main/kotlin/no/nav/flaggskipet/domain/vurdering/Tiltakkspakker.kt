package no.nav.flaggskipet.domain.vurdering

object FylkeKode {
    const val TRONDHEIM = "50"
    const val GAMMEL_TROMS = "54"
    const val TROMS = "55"
}

class TiltakspakkeEnRegel(override val tiltakspakke: Tiltakspakke) : Regel {

    private val fylkerIScopet = setOf(FylkeKode.TRONDHEIM, FylkeKode.TROMS, FylkeKode.GAMMEL_TROMS)

    override fun vurder(grunnlag: VurderingsGrunnlag): Deltakelse = when {
        grunnlag.virksomhet.adresse.fylke() !in fylkerIScopet ->
            Deltakelse.UTENFOR_SCOPE

        erSann(0.5) ->
            Deltakelse.DELTAR

        else ->
            Deltakelse.DELTAR_IKKE
    }
}

fun getGjeldendeTiltakspakker(): List<Regel> = listOf(
    TiltakspakkeEnRegel(Tiltakspakke("TILTAKSPAKKE_EN", null)),
).filter { it.tiltakspakke.erGjeldene() }
