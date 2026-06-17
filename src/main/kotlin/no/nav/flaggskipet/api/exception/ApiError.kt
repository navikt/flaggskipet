package no.nav.flaggskipet.api.exception

import kotlinx.serialization.Serializable
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
}

@Serializable
data class ApiError(
    val status: Int,
    val type: ErrorType,
    val message: String,
    val path: String? = null,
    val timestamp: String = Instant.now().toString(),
)
