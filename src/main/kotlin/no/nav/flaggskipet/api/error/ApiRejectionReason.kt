package no.nav.flaggskipet.api.error

enum class ApiRejectionReason {
    EMPTY_ORGNUMRE,
    TOO_MANY_ORGNUMRE,
    INVALID_ORGNUMMER_FORMAT,
    MISSING_BEARER_TOKEN,
    INACTIVE_TOKEN,
    INSUFFICIENT_SECURITY_LEVEL,
}
