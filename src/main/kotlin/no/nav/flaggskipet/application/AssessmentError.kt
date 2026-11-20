package no.nav.flaggskipet.application

import no.nav.flaggskipet.application.port.EregError

sealed interface AssessmentError {
    data object EmptyOrgnumre : AssessmentError
    data object TooManyOrgnumre : AssessmentError
    data object InvalidOrgnummerFormat : AssessmentError
    data class EregUnavailable(val cause: EregError) : AssessmentError
}
