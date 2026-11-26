package no.nav.flaggskipet.api.auth

sealed interface AuthError {
    data object MissingBearerToken : AuthError
    data object InactiveToken : AuthError
    data object InsufficientSecurityLevel : AuthError

    // The cause is kept for the sanitized stack trace; never log its message.
    class IntrospectionFailed(val cause: Throwable) : AuthError
}
