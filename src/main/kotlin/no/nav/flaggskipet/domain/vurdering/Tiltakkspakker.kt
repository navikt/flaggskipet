package no.nav.flaggskipet.domain.vurdering

object FylkeKode {
    const val TRONDHEIM = "50"
    const val TROMS = "55"
}

class TiltakspakkeEnRegel(override val tiltakspakke: Tiltakspakke) : Regel {

    private val fylkerIScopet = setOf(FylkeKode.TRONDHEIM, FylkeKode.TROMS)

    override fun vurder(grunnlag: VurderingsGrunnlag): Deltakelse = when {
        grunnlag.virksomhet.adresse.fylke() !in fylkerIScopet ->
            Deltakelse.UTENFOR_SCOPE

        grunnlag.metadata.erSann(0.5) ->
            Deltakelse.DELTAR

        else ->
            Deltakelse.DELTAR_IKKE
    }
}

fun getGjeldendeTiltakspakker(): List<Regel> {
    return listOf(
        TiltakspakkeEnRegel(Tiltakspakke("TILTAKSPAKKE_EN", null))
    ).filter { it.tiltakspakke.erGjeldene() }
}
