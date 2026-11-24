package io.github.slavikjunior.syncclip.server.feature.auth.api

import io.github.slavikjunior.syncclip.server.contracts.dto.auth.FirebaseLoginRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestExchangeRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.LoginRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RegisterRequest
import io.github.slavikjunior.syncclip.server.contracts.error.ValidationException
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.feature.auth.domain.FirebaseLoginCommand
import io.github.slavikjunior.syncclip.server.feature.auth.domain.GuestExchangeCommand
import io.github.slavikjunior.syncclip.server.feature.auth.domain.LoginCommand
import io.github.slavikjunior.syncclip.server.feature.auth.domain.RegisterCommand
import io.github.slavikjunior.syncclip.server.feature.auth.domain.RequestMeta
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.userAgent

internal fun RegisterRequest.toCommand() = RegisterCommand(
    email = Validators.email(email),
    password = Validators.password(password),
    deviceId = Validators.uuid(deviceId, "deviceId"),
    deviceName = Validators.deviceName(deviceName),
    osType = Validators.osType(osType),
)

internal fun LoginRequest.toCommand() = LoginCommand(
    email = Validators.email(email),
    password = Validators.password(password),
    deviceId = Validators.uuid(deviceId, "deviceId"),
    deviceName = Validators.deviceName(deviceName),
    osType = Validators.osType(osType),
)

internal fun FirebaseLoginRequest.toCommand() = FirebaseLoginCommand(
    deviceId = Validators.uuid(deviceId, "deviceId"),
    deviceName = Validators.deviceName(deviceName),
    osType = Validators.osType(osType),
)

internal fun GuestExchangeRequest.toCommand() = GuestExchangeCommand(    inviteToken = inviteToken.trim().also {
        if (it.isEmpty()) throw ValidationException("inviteToken", "empty")
    },
    deviceId = Validators.uuid(deviceId, "deviceId"),
    deviceName = Validators.deviceName(deviceName),
    osType = Validators.osType(osType),
)

internal fun ApplicationCall.requestMeta(clientIp: String) = RequestMeta(
    ip = Validators.ipOrNull(clientIp),
    userAgent = request.userAgent()?.take(512),
    // Отдельный заголовок от клиента; UA больше не выдаётся за модель (LOW-08).
    deviceModel = request.headers[DEVICE_MODEL_HEADER]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(255),
)

internal const val DEVICE_MODEL_HEADER: String = "X-Device-Model"
