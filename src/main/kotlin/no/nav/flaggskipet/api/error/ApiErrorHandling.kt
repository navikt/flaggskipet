package no.nav.flaggskipet.api.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException

internal fun ApplicationCall.logException(cause: Throwable) {
    val logExceptionMessage = "Caught ${cause::class.simpleName} exception"
    application.log.error(logExceptionMessage, cause)
}

internal fun determineApiError(cause: Throwable, path: String): ApiError = when (cause) {
    is BadRequestException -> cause.toApiError(path)
    is NotFoundException -> cause.toApiError(path)
    is ApiErrorException -> cause.toApiError(path)
    is IllegalArgumentException -> ApiErrorException.BadRequest(
        errorMessage = cause.message ?: "Illegal argument",
        type = ErrorType.ILLEGAL_ARGUMENT,
        cause = cause,
    ).toApiError(path)

    else -> ApiError(
        status = HttpStatusCode.InternalServerError.value,
        type = ErrorType.INTERNAL_SERVER_ERROR,
        message = cause.message ?: "Internal server error",
        path = path,
    )
}

private fun BadRequestException.toApiError(path: String): ApiError = ApiError(
    status = HttpStatusCode.BadRequest.value,
    type = ErrorType.BAD_REQUEST,
    message = message ?: "Bad request",
    path = path,
)

private fun NotFoundException.toApiError(path: String): ApiError = ApiError(
    status = HttpStatusCode.NotFound.value,
    type = ErrorType.NOT_FOUND,
    message = message ?: "Not found",
    path = path,
)
