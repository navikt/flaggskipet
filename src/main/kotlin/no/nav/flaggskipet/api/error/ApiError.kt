package no.nav.flaggskipet.api.error

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Instant

@Serializable
enum class ErrorType {
    AUTHENTICATION_ERROR,
    AUTHORIZATION_ERROR,
    NOT_FOUND,
    INTERNAL_SERVER_ERROR,
    ILLEGAL_ARGUMENT,
    BAD_REQUEST,
    CONFLICT,
    TEXAS_INTROSPECTION_FAILED,
}

@Serializable
data class ApiError(
    val status: Int,
    val type: ErrorType,
    val message: String,
    val path: String? = null,
    val timestamp: String = Instant.now().toString(),
    // Log only; never sent to the client.
    @Transient val rejectionReason: ApiRejectionReason? = null,
    @Transient val failureReason: ApiFailureReason? = null,
)
