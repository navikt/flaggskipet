package no.nav.flaggskipet.api.error

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import no.nav.flaggskipet.api.auth.AuthError
import no.nav.flaggskipet.application.AssessmentError
import no.nav.flaggskipet.application.port.EregError

context(ctx: RoutingContext)
internal suspend inline fun <reified T : Any> Either<AssessmentError, T>.toResponse() = fold(
    ifLeft = { ctx.call.respondApiError(it.toApiError()) },
    ifRight = { ctx.call.respond(it) },
)

internal fun AssessmentError.toApiError(): ApiError = when (this) {
    AssessmentError.EmptyOrgnumre -> badRequest(ApiRejectionReason.EMPTY_ORGNUMRE)

    AssessmentError.TooManyOrgnumre -> badRequest(ApiRejectionReason.TOO_MANY_ORGNUMRE)

    AssessmentError.InvalidOrgnummerFormat -> badRequest(ApiRejectionReason.INVALID_ORGNUMMER_FORMAT)

    is AssessmentError.EregUnavailable -> internalServerError(
        failureReason = when (cause) {
            is EregError.UnexpectedStatus -> ApiFailureReason.EREG_UNEXPECTED_STATUS
            EregError.Timeout -> ApiFailureReason.EREG_TIMEOUT
            EregError.RequestFailed -> ApiFailureReason.EREG_REQUEST_FAILED
        },
    )
}

internal fun AuthError.toApiError(path: String): ApiError = when (this) {
    AuthError.MissingBearerToken -> unauthorized("Missing bearer token", ApiRejectionReason.MISSING_BEARER_TOKEN, path)

    AuthError.InactiveToken -> unauthorized("Token is not active", ApiRejectionReason.INACTIVE_TOKEN, path)

    AuthError.InsufficientSecurityLevel -> ApiError(
        status = HttpStatusCode.Forbidden.value,
        type = ErrorType.AUTHORIZATION_ERROR,
        message = "Token does not meet the required security level",
        path = path,
        rejectionReason = ApiRejectionReason.INSUFFICIENT_SECURITY_LEVEL,
    )

    is AuthError.IntrospectionFailed -> ApiError(
        status = HttpStatusCode.InternalServerError.value,
        type = ErrorType.TEXAS_INTROSPECTION_FAILED,
        message = "Authentication service unavailable",
        path = path,
    )
}

private fun unauthorized(message: String, reason: ApiRejectionReason, path: String) = ApiError(
    status = HttpStatusCode.Unauthorized.value,
    type = ErrorType.AUTHENTICATION_ERROR,
    message = message,
    path = path,
    rejectionReason = reason,
)

private fun badRequest(reason: ApiRejectionReason) = ApiError(
    status = HttpStatusCode.BadRequest.value,
    type = ErrorType.BAD_REQUEST,
    message = "Bad request",
    rejectionReason = reason,
)

private fun internalServerError(failureReason: ApiFailureReason) = ApiError(
    status = HttpStatusCode.InternalServerError.value,
    type = ErrorType.INTERNAL_SERVER_ERROR,
    message = "Internal server error",
    failureReason = failureReason,
)

internal suspend fun ApplicationCall.respondApiError(apiError: ApiError) {
    val withPath = apiError.copy(path = request.path())
    logEitherError(withPath)
    respond(HttpStatusCode.fromValue(withPath.status), withPath)
}
