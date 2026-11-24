package io.github.slavikjunior.syncclip.server.app.plugins

import io.github.slavikjunior.syncclip.server.app.i18n.MessageCatalog
import io.github.slavikjunior.syncclip.server.contracts.error.AppException
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.acceptLanguage
import io.ktor.server.request.contentType
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

/**
 * Единственное место маппинга исключение → HTTP (§4.3).
 *
 * Клиентские ошибки (400/405/413/415) не должны выглядеть как 5xx и не
 * логируются стектрейсом (FUNC-01/LOW-04). Для 5xx пишем только callId и
 * класс исключения: SQL-сообщения PostgreSQL могут содержать PII (SEC-12).
 */
fun Application.configureStatusPages(catalog: MessageCatalog) {
    val logger = LoggerFactory.getLogger("StatusPages")
    install(StatusPages) {
        exception<AppException> { call, cause ->
            val requestId = call.callId
            val status = HttpStatusCode.fromValue(cause.code.httpStatus)
            val message = catalog.message(cause.code.restCode, catalog.locale(call.request.acceptLanguage()))
            val body = ErrorResponse(
                error = cause.code.restCode,
                message = message,
                details = cause.details.ifEmpty { null },
                retryAfter = cause.retryAfterSeconds,
            )
            cause.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            if (cause.code.httpStatus >= 500) {
                logger.error("request failed callId=$requestId error=${cause.code.restCode} type=${cause.javaClass.simpleName}")
            } else {
                logger.warn("request rejected callId=$requestId error=${cause.code.restCode}")
            }
            call.respond(status, body)
        }

        // 4xx от Ktor: битый JSON, пустое тело, неподдерживаемый Content-Type, огромное тело.
        exception<ContentTransformationException> { call, cause ->
            if (cause.hasCause<UnsupportedMediaTypeException>() || call.hasNonJsonContentType()) {
                respondError(call, catalog, ErrorCode.UNSUPPORTED_MEDIA_TYPE)
            } else {
                respondError(call, catalog, ErrorCode.VALIDATION_ERROR, mapOf("reason" to "malformed_request"))
            }
        }

        exception<BadRequestException> { call, cause ->
            val wrongMediaType = cause.hasCause<UnsupportedMediaTypeException>() || call.hasNonJsonContentType()
            if (wrongMediaType) {
                respondError(call, catalog, ErrorCode.UNSUPPORTED_MEDIA_TYPE)
            } else {
                respondError(call, catalog, ErrorCode.VALIDATION_ERROR, mapOf("reason" to "malformed_request"))
            }
        }

        // Ошибка парсинга kotlinx.serialization приходит как SerializationException.
        exception<SerializationException> { call, _ ->
            respondError(call, catalog, ErrorCode.VALIDATION_ERROR, mapOf("reason" to "malformed_json"))
        }

        exception<UnsupportedMediaTypeException> { call, _ ->
            respondError(call, catalog, ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        }

        exception<PayloadTooLargeException> { call, _ ->
            respondError(call, catalog, ErrorCode.PAYLOAD_TOO_LARGE)
        }

        // Отмена корутины (shutdown/timeout) — не ошибка сервера (LOW-06).
        exception<CancellationException> { _, cause -> throw cause }

        exception<Throwable> { call, cause ->
            val requestId = call.callId
            logger.error("unhandled error callId=$requestId type=${cause.javaClass.simpleName}")
            respondError(
                call,
                catalog,
                ErrorCode.INTERNAL_ERROR,
                details = requestId?.let { mapOf("callId" to it) },
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            val message = catalog.message(ErrorCode.NOT_FOUND.restCode, catalog.locale(call.request.acceptLanguage()))
            call.respond(status, ErrorResponse(ErrorCode.NOT_FOUND.restCode, message))
        }

        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            val message = catalog.message(ErrorCode.METHOD_NOT_ALLOWED.restCode, catalog.locale(call.request.acceptLanguage()))
            call.respond(status, ErrorResponse(ErrorCode.METHOD_NOT_ALLOWED.restCode, message))
        }

        status(HttpStatusCode.UnsupportedMediaType) { call, status ->
            val message = catalog.message(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.restCode,
                catalog.locale(call.request.acceptLanguage()),
            )
            call.respond(status, ErrorResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE.restCode, message))
        }
    }
}

private suspend fun respondError(
    call: io.ktor.server.application.ApplicationCall,
    catalog: MessageCatalog,
    code: ErrorCode,
    details: Map<String, String>? = null,
) {
    val message = catalog.message(code.restCode, catalog.locale(call.request.acceptLanguage()))
    call.respond(
        HttpStatusCode.fromValue(code.httpStatus),
        ErrorResponse(code.restCode, message, details = details?.ifEmpty { null }),
    )
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence(this) { it.cause }.any { it is T }

private fun io.ktor.server.application.ApplicationCall.hasNonJsonContentType(): Boolean {
    val contentType = request.contentType()
    return contentType != ContentType.Any && !contentType.match(ContentType.Application.Json)
}
