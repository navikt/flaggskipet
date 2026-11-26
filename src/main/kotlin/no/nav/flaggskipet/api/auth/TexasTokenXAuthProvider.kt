package no.nav.flaggskipet.api.auth

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.authorization
import no.nav.flaggskipet.infrastructure.clients.texas.IDENTITY_PROVIDER_TOKENX
import no.nav.flaggskipet.infrastructure.clients.texas.TexasClient

const val TOKENX_AUTHENTICATION = "tokenx"

// Omit pid deliberately; see TexasIntrospectionResponse.
data class TokenXPrincipal(
    val clientId: String?,
    val acr: String?,
)

// ID-porten uses both the legacy Level4 and the newer idporten-loa-high for its
// highest assurance level. Consumers sign in using Level4, while tokens from the
// development token generator use the new value. Both are accepted.
private val approvedAcrValues = setOf("Level4", "idporten-loa-high")

class TexasTokenXAuthProvider(
    config: Config,
) : AuthenticationProvider(config) {
    private val texasClient = config.texasClient

    class Config internal constructor(
        name: String,
        val texasClient: TexasClient,
    ) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val bearerToken = context.call.request.bearerToken()
        authenticate(texasClient, bearerToken).fold(
            ifLeft = { throw AuthException(it) },
            ifRight = { context.principal(it) },
        )
    }
}

internal suspend fun authenticate(
    texasClient: TexasClient,
    bearerToken: String?,
): Either<AuthError, TokenXPrincipal> = either {
    ensureNotNull(bearerToken) { AuthError.MissingBearerToken }

    // Arrow's catch rethrows CancellationException, so cancellation propagates unchanged.
    val introspection = catch({ texasClient.introspectToken(IDENTITY_PROVIDER_TOKENX, bearerToken) }) {
        raise(AuthError.IntrospectionFailed(it))
    }

    ensure(introspection.active) { AuthError.InactiveToken }
    ensure(introspection.acr in approvedAcrValues) { AuthError.InsufficientSecurityLevel }

    TokenXPrincipal(clientId = introspection.clientId, acr = introspection.acr)
}

internal fun ApplicationRequest.bearerToken(): String? {
    // The String overload throws ParseException; the ApplicationRequest overload throws BadRequestException (400).
    val header = try {
        authorization()?.let { parseAuthorizationHeader(it) }
    } catch (_: IllegalArgumentException) {
        null
    }

    return (header as? HttpAuthHeader.Single)
        ?.takeIf { it.authScheme.equals(AuthScheme.Bearer, ignoreCase = true) }
        ?.blob
}

fun AuthenticationConfig.texasTokenX(name: String, texasClient: TexasClient) {
    register(TexasTokenXAuthProvider(TexasTokenXAuthProvider.Config(name, texasClient)))
}

fun Application.installAuthentication() {
    val texasClient: TexasClient by dependencies
    install(Authentication) {
        texasTokenX(TOKENX_AUTHENTICATION, texasClient)
    }
}
