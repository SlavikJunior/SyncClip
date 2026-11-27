package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import kotlinx.serialization.Serializable

/**
 * Вход/регистрация по email и паролю на сервере.
 *
 * Используется там, где Firebase недоступен (JVM/Desktop): серверный
 * `/v1/auth/register` и `/v1/auth/login` принимают ровно этот набор полей.
 */
@Serializable
internal data class PasswordAuthRequestDto(
    val email: String,
    val password: String,
    val deviceId: String,
    val deviceName: String,
    val osType: String,
)
