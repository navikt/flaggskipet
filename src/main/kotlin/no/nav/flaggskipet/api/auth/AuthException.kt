package no.nav.flaggskipet.api.auth

import no.nav.flaggskipet.api.error.ApiRejectionReason

/** Thrown from the authentication provider, because Ktor's auth plugin cannot return an Either. */
sealed class AuthException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class Unauthorized(
        message: String,
        val rejectionReason: ApiRejectionReason,
    ) : AuthException(message)

    class Forbidden(
        message: String,
        val rejectionReason: ApiRejectionReason,
    ) : AuthException(message)

    class IntrospectionFailed(
        cause: Throwable,
    ) : AuthException("Authentication service unavailable", cause)
}
