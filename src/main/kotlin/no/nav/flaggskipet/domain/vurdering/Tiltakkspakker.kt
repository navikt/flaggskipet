package no.nav.flaggskipet.domain.vurdering

object FylkeKode {
    const val TRONDHEIM = "50"
    const val TROMS = "55"
}

class TiltakspakkeEnRegel(override val tiltakspakke: Tiltakspakke) : Regel {

    private val fylkerSomErInScope = setOf(FylkeKode.TRONDHEIM, FylkeKode.TROMS)

    override fun vurder(grunnlag: VurderingsGrunnlag): Deltakelse {
        if (!fylkerSomErInScope.contains(grunnlag.virksomhet.adresse.fylke())) return Deltakelse.UTENFOR_SCOPE

        return if (grunnlag.metadata.erSann(0.5)) {
            Deltakelse.DELTAR
        } else {
            Deltakelse.DELTAR_IKKE
        }
    }
}

val gyldigePakker = listOf(TiltakspakkeEnRegel(Tiltakspakke("TILTAKSPAKKE_EN", null))).filter { it.tiltakspakke.gyldig() }
