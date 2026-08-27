package no.nav.flaggskipet.domain.vurdering

enum class Vurderingsgrunn {
    FYLKE_I_SCOPE,
    FYLKE_UTENFOR_SCOPE,
    MANGLER_ADRESSE,
    UGYLDIG_KOMMUNENUMMER,
    UTGATT_FYLKESKODE,
    IKKE_REGISTRERT,
}
