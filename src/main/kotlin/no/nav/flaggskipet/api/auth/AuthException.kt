package no.nav.flaggskipet.api.auth

/** Thrown once from the authentication provider, because Ktor's auth plugin cannot return an Either. */
class AuthException(
    val error: AuthError,
) : RuntimeException(
    // Class name only: the message must never contain token or Texas content.
    "Authentication failed: ${error::class.simpleName}",
    (error as? AuthError.IntrospectionFailed)?.cause,
)
