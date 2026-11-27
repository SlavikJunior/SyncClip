package io.github.slavikjunior.syncclip.feature.auth.impl.data.repository

import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthFailure
import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthGateway
import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthResult
import io.github.slavikjunior.syncclip.core.firebase.PushTokenSource
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.AuthApi
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.AuthHttpException
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.AuthTokensDto
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.FcmRegisterRequestDto
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.FirebaseLoginRequestDto
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.PasswordAuthRequestDto
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthFailureReason
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthResult
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.repository.AuthRepository
import kotlinx.io.IOException

/**
 * Двухшаговый вход: Firebase Auth → `POST /auth/firebase` → сессия сервера.
 *
 * После сохранения сессии сразу регистрируется FCM-токен: сервер привязывает
 * push к устройству из JWT, поэтому нужен уже полученный access token.
 */
internal class FirebaseAuthRepository(
    private val gateway: FirebaseAuthGateway,
    private val api: AuthApi,
    private val sessionStore: SessionStore,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val pushTokens: PushTokenSource,
    private val lockout: E2eeLockoutStore? = null,
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): AuthResult =
        authenticate(
            block = { gateway.signInWithEmail(email, password) },
            passwordFallback = { serverAuth(email, password, register = false) },
        )

    override suspend fun signUp(email: String, password: String): AuthResult =
        authenticate(
            block = { gateway.signUpWithEmail(email, password) },
            passwordFallback = { serverAuth(email, password, register = true) },
        )

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): AuthResult =
        authenticate(block = { gateway.signInWithGoogle(idToken, accessToken) })

    override suspend fun signOut() {
        gateway.signOut()
        sessionStore.clear()
        // Блокировка мастер-пароля принадлежит аккаунту: новый вход не должен
        // наследовать таймер предыдущего пользователя.
        lockout?.clear()
    }

    override suspend fun registerPushToken() {
        if (sessionStore.state.value !is SessionState.SignedIn) return
        val token = pushTokens.currentToken()?.takeIf { it.isNotBlank() } ?: return
        val identity = deviceIdentityStore.identity()
        api.registerFcmToken(FcmRegisterRequestDto(deviceId = identity.deviceId, fcmToken = token))
    }

    private suspend fun authenticate(
        block: suspend () -> FirebaseAuthResult,
        passwordFallback: (suspend () -> AuthResult)? = null,
    ): AuthResult = when (val firebase = block()) {
        is FirebaseAuthResult.Failure -> {
            // Firebase не поддерживается на JVM — Desktop входит напрямую на сервер.
            if (firebase.reason == FirebaseAuthFailure.NOT_SUPPORTED && passwordFallback != null) {
                passwordFallback()
            } else {
                AuthResult.Failure(firebase.reason.toReason())
            }
        }

        is FirebaseAuthResult.Success -> {
                // Токен только что выдан входом; принудительный refresh — лишний
                // сетевой круг (на медленной сети Firebase отдаёт таймаут).
                val idToken = gateway.idToken(forceRefresh = false)
                if (idToken.isNullOrBlank()) {
                    AuthResult.Failure(AuthFailureReason.UNKNOWN)
                } else {
                    exchange(idToken)
                }
        }
    }

    /**
     * Резервный вход без Firebase: `POST /v1/auth/login|register`.
     *
     * Так работает Desktop (Firebase на JVM недоступен) и любой клиент, если
     * Firebase вернул `NOT_SUPPORTED`.
     */
    private suspend fun serverAuth(
        email: String,
        password: String,
        register: Boolean,
    ): AuthResult {
        val identity = deviceIdentityStore.identity()
        val request = PasswordAuthRequestDto(
            email = email.trim(),
            password = password,
            deviceId = identity.deviceId,
            deviceName = identity.deviceName,
            osType = identity.osType,
        )
        val result = if (register) {
            api.registerPassword(request)
        } else {
            api.loginPassword(request)
        }
        return result.fold(
            onSuccess = { tokens -> saveSession(tokens) },
            onFailure = { error -> AuthResult.Failure(error.toReason()) },
        )
    }

    private suspend fun saveSession(tokens: AuthTokensDto): AuthResult {
        sessionStore.save(
            SessionSnapshot(
                userId = tokens.userId,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            ),
        )
        // Ошибка push-регистрации не должна отменять успешный вход.
        runCatching { registerPushToken() }
        return AuthResult.Success
    }

    private suspend fun exchange(idToken: String): AuthResult {
        val identity = deviceIdentityStore.identity()
        val request = FirebaseLoginRequestDto(
            idToken = idToken,
            deviceId = identity.deviceId,
            deviceName = identity.deviceName,
            osType = identity.osType,
        )
        return api.login(request).fold(
            onSuccess = { tokens -> saveSession(tokens) },
            onFailure = { error -> AuthResult.Failure(error.toReason()) },
        )
    }
}

private fun FirebaseAuthFailure.toReason(): AuthFailureReason = when (this) {
    FirebaseAuthFailure.INVALID_CREDENTIALS -> AuthFailureReason.INVALID_CREDENTIALS
    FirebaseAuthFailure.EMAIL_ALREADY_IN_USE -> AuthFailureReason.EMAIL_ALREADY_IN_USE
    FirebaseAuthFailure.WEAK_PASSWORD -> AuthFailureReason.WEAK_PASSWORD
    FirebaseAuthFailure.INVALID_EMAIL -> AuthFailureReason.INVALID_EMAIL
    FirebaseAuthFailure.EMAIL_NOT_VERIFIED -> AuthFailureReason.EMAIL_NOT_VERIFIED
    FirebaseAuthFailure.NETWORK -> AuthFailureReason.NETWORK
    FirebaseAuthFailure.NOT_SUPPORTED -> AuthFailureReason.NOT_SUPPORTED
    FirebaseAuthFailure.UNKNOWN -> AuthFailureReason.UNKNOWN
}

private fun Throwable.toReason(): AuthFailureReason = when (this) {
    is AuthHttpException -> when {
        errorCode == "email_already_registered" -> AuthFailureReason.EMAIL_ALREADY_IN_USE
        errorCode == "invalid_credentials" -> AuthFailureReason.INVALID_CREDENTIALS
        errorCode == "email_not_verified" -> AuthFailureReason.EMAIL_NOT_VERIFIED
        errorCode == "validation_error" -> AuthFailureReason.INVALID_INPUT
        statusCode == 401 -> AuthFailureReason.INVALID_CREDENTIALS
        statusCode == 409 -> AuthFailureReason.EMAIL_ALREADY_IN_USE
        statusCode == 400 -> AuthFailureReason.INVALID_INPUT
        else -> AuthFailureReason.UNKNOWN
    }

    is IOException -> AuthFailureReason.NETWORK
    else -> AuthFailureReason.UNKNOWN
}
