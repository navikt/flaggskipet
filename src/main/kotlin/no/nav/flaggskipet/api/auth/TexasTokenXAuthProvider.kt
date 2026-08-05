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
import no.nav.flaggskipet.api.error.ApiErrorException
import no.nav.flaggskipet.infrastructure.clients.texas.IDENTITY_PROVIDER_TOKENX
import no.nav.flaggskipet.infrastructure.clients.texas.TexasClient
import org.slf4j.LoggerFactory

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

private val logger = LoggerFactory.getLogger(TexasTokenXAuthProvider::class.java)

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

        val introspection = try {
            texasClient.introspectToken(IDENTITY_PROVIDER_TOKENX, bearerToken)
        } catch (exception: Exception) {
            logger.error("Token introspection mot Texas feilet", exception)
            throw ApiErrorException.Unauthorized("Token introspection failed", exception)
        }

        if (!introspection.active) {
            logger.warn("Avviste request med inaktivt token: {}", introspection.error ?: "ukjent årsak")
            throw ApiErrorException.Unauthorized("Token is not active")
        }

        if (introspection.acr !in godkjenteAcrVerdier) {
            logger.warn(
                "Avviste request med for lavt sikkerhetsnivå: acr={}, client_id={}",
                introspection.acr,
                introspection.clientId,
            )
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
