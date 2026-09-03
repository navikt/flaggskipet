package no.nav.flaggskipet.api.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.authorization
import kotlinx.coroutines.CancellationException
import no.nav.flaggskipet.api.error.ApiErrorException
import no.nav.flaggskipet.api.error.ErrorType
import no.nav.flaggskipet.infrastructure.clients.texas.IDENTITY_PROVIDER_TOKENX
import no.nav.flaggskipet.infrastructure.clients.texas.TexasClient

const val TOKENX_AUTHENTICATION = "tokenx"

// pid holdes bevisst utenfor, se TexasIntrospectionResponse.
data class TokenXPrincipal(
    val clientId: String?,
    val acr: String?,
)

// ID-porten bruker både legacy-navnet Level4 og det nye idporten-loa-high om høyeste
// sikkerhetsnivå. Konsumentene logger inn med Level4, mens token fra token-generatoren
// i dev bærer den nye verdien. Begge representerer samme nivå og godtas.
private val godkjenteAcrVerdier = setOf("Level4", "idporten-loa-high")

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
            ?: throw ApiErrorException.Unauthorized("Missing bearer token")

        val introspection = introspectTokenForAuthentication(texasClient, bearerToken)

        if (!introspection.active) {
            throw ApiErrorException.Unauthorized("Token is not active")
        }

        if (introspection.acr !in godkjenteAcrVerdier) {
            throw ApiErrorException.Forbidden("Token does not meet the required security level")
        }

        context.principal(
            TokenXPrincipal(
                clientId = introspection.clientId,
                acr = introspection.acr,
            ),
        )
    }
}

internal suspend fun introspectTokenForAuthentication(
    texasClient: TexasClient,
    bearerToken: String,
) = try {
    texasClient.introspectToken(IDENTITY_PROVIDER_TOKENX, bearerToken)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: Exception) {
    throw ApiErrorException.InternalServerError(
        errorMessage = "Authentication service unavailable",
        cause = exception,
        type = ErrorType.TEXAS_INTROSPECTION_FAILED,
    )
}

internal fun ApplicationRequest.bearerToken(): String? = authorization()
    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
    ?.drop("Bearer ".length)
    ?.takeIf { it.isNotBlank() }

fun AuthenticationConfig.texasTokenX(name: String, texasClient: TexasClient) {
    register(TexasTokenXAuthProvider(TexasTokenXAuthProvider.Config(name, texasClient)))
}

fun Application.installAuthentication() {
    val texasClient: TexasClient by dependencies
    install(Authentication) {
        texasTokenX(TOKENX_AUTHENTICATION, texasClient)
    }
}
