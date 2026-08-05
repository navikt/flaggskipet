package no.nav.flaggskipet.infrastructure.clients.texas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val IDENTITY_PROVIDER_TOKENX = "tokenx"

class TexasClient(
    private val httpClient: HttpClient,
    private val config: TexasConfig,
) {
    suspend fun introspectToken(identityProvider: String, token: String): TexasIntrospectionResponse = httpClient
        .post(config.introspectionEndpoint.toString()) {
            contentType(ContentType.Application.Json)
            setBody(
                TexasIntrospectionRequest(
                    identityProvider = identityProvider,
                    token = token,
                ),
            )
        }.body<TexasIntrospectionResponse>()
}

@Serializable
internal data class TexasIntrospectionRequest(
    @SerialName("identity_provider")
    val identityProvider: String,
    val token: String,
)

// Texas returnerer alle claims i tokenet ved aktivt token. Modellen tar bare med
// claims appen faktisk bruker — pid holdes bevisst utenfor så den ikke kan lekke til logg.
@Serializable
data class TexasIntrospectionResponse(
    val active: Boolean,
    val error: String? = null,
    val acr: String? = null,
    @SerialName("client_id")
    val clientId: String? = null,
)
