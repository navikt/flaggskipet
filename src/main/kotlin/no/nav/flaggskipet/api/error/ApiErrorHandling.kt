package no.nav.flaggskipet.api.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.path
import kotlinx.coroutines.TimeoutCancellationException
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.Logger

private const val VURDER_TILTAKSPAKKER_PATH = "/api/v1/tiltakspakker/vurdering"

private const val API_REQUEST_REJECTED_EVENT = "api_request_rejected"
private const val API_REQUEST_FAILED_EVENT = "api_request_failed"

internal enum class ApiOperation(
    val logValue: String,
) {
    VURDER_TILTAKSPAKKER("vurder_tiltakspakker"),
    BEHANDLE_API_KALL("behandle_api_kall"),
}

internal fun ApplicationCall.logApiError(
    apiError: ApiError,
    cause: Throwable,
) {
    val operation = when (request.path()) {
        VURDER_TILTAKSPAKKER_PATH -> ApiOperation.VURDER_TILTAKSPAKKER
        else -> ApiOperation.BEHANDLE_API_KALL
    }

    application.log.logApiError(apiError, cause, operation)
}

internal fun Logger.logApiError(
    apiError: ApiError,
    cause: Throwable,
    operation: ApiOperation,
) {
    if (apiError.status in 400..499) {
        warn(
            "Avviser API-kall: {} {} {}",
            kv("event_type", API_REQUEST_REJECTED_EVENT),
            kv("error_code", apiError.type.name),
            kv("operation", operation.logValue),
        )
        return
    }

    error(
        "Uventet API-feil: {} {} {} {}",
        kv("event_type", API_REQUEST_FAILED_EVENT),
        kv("error_code", apiError.type.name),
        kv("operation", operation.logValue),
        kv("exception_type", cause.safeExceptionType()),
        cause.withoutDynamicContent(),
    )
}

private fun Throwable.safeExceptionType(): String = when (this) {
    is ApiErrorException -> "ApiErrorException"
    is TimeoutCancellationException -> "TimeoutCancellationException"
    is IllegalStateException -> "IllegalStateException"
    else -> "UnknownException"
}

private fun Throwable.withoutDynamicContent(): Throwable = sanitizedCopy(safeExceptionType())

private fun Throwable.sanitizedCopy(
    exceptionType: String = this::class.simpleName ?: "UnknownException",
): Throwable = RuntimeException(exceptionType, cause?.sanitizedCopy()).also { safeThrowable ->
    safeThrowable.stackTrace = stackTrace
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
        message = "Internal server error",
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
