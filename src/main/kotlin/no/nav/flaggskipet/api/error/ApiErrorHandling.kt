package no.nav.flaggskipet.api.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.path
import kotlinx.coroutines.TimeoutCancellationException
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.flaggskipet.api.auth.AuthException
import org.slf4j.Logger

private const val ASSESS_TILTAKSPAKKER_PATH = "/api/v1/tiltakspakker/vurdering"

private const val API_REQUEST_REJECTED_EVENT = "api_request_rejected"
private const val API_REQUEST_FAILED_EVENT = "api_request_failed"

internal enum class ApiOperation(
    val logValue: String,
) {
    ASSESS_TILTAKSPAKKER("vurder_tiltakspakker"),
    HANDLE_API_CALL("behandle_api_kall"),
}

internal fun ApplicationCall.logApiError(
    apiError: ApiError,
    cause: Throwable,
) {
    application.log.logApiError(apiError, cause, apiOperation())
}

internal fun ApplicationCall.logApiError(apiError: ApiError) {
    val operation = apiOperation()
    if (apiError.rejectionReason != null) {
        application.log.logRejection(apiError, apiError.rejectionReason.name, operation)
        return
    }

    application.log.error(
        "Unexpected API error: {} {} {} {}",
        kv("event_type", API_REQUEST_FAILED_EVENT),
        kv("error_code", apiError.type.name),
        kv("operation", operation.logValue),
        kv("failure_reason", apiError.failureReason?.name ?: "UNSPECIFIED"),
    )
}

private fun ApplicationCall.apiOperation(): ApiOperation = when (request.path()) {
    ASSESS_TILTAKSPAKKER_PATH -> ApiOperation.ASSESS_TILTAKSPAKKER
    else -> ApiOperation.HANDLE_API_CALL
}

private fun Logger.logRejection(
    apiError: ApiError,
    rejectionReason: String,
    operation: ApiOperation,
) {
    warn(
        "Rejecting API request: {} {} {} {}",
        kv("event_type", API_REQUEST_REJECTED_EVENT),
        kv("error_code", apiError.type.name),
        kv("operation", operation.logValue),
        kv("rejection_reason", rejectionReason),
    )
}

internal fun Logger.logApiError(
    apiError: ApiError,
    cause: Throwable,
    operation: ApiOperation,
) {
    val explicitRejection = when (cause) {
        is AuthException.Unauthorized, is AuthException.Forbidden -> true
        is BadRequestException, is NotFoundException -> true
        else -> false
    }
    if (apiError.status in 400..499 && explicitRejection) {
        logRejection(apiError, apiError.rejectionReason?.name ?: "UNSPECIFIED", operation)
        return
    }

    error(
        "Unexpected API error: {} {} {} {}",
        kv("event_type", API_REQUEST_FAILED_EVENT),
        kv("error_code", apiError.type.name),
        kv("operation", operation.logValue),
        kv("exception_type", cause.safeExceptionType()),
        cause.withoutDynamicContent(),
    )
}

private fun Throwable.safeExceptionType(): String = when (this) {
    is AuthException -> "AuthException"
    is TimeoutCancellationException -> "TimeoutCancellationException"
    is IllegalStateException -> "IllegalStateException"
    is IllegalArgumentException -> "IllegalArgumentException"
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

    is AuthException -> cause.toApiError(path)

    is IllegalArgumentException -> ApiError(
        status = HttpStatusCode.BadRequest.value,
        type = ErrorType.ILLEGAL_ARGUMENT,
        message = cause.message ?: "Illegal argument",
        path = path,
    )

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
