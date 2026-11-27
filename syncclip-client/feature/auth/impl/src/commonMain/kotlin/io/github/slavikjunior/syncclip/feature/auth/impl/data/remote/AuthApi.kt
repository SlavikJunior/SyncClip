package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

/** Порт серверных методов авторизации и push-регистрации. */
internal interface AuthApi {

    suspend fun login(request: FirebaseLoginRequestDto): Result<AuthTokensDto>

    /** `POST /v1/auth/login` — Desktop/резервный путь без Firebase. */
    suspend fun loginPassword(request: PasswordAuthRequestDto): Result<AuthTokensDto>

    /** `POST /v1/auth/register` — Desktop/резервный путь без Firebase. */
    suspend fun registerPassword(request: PasswordAuthRequestDto): Result<AuthTokensDto>

    suspend fun registerFcmToken(request: FcmRegisterRequestDto): Result<Unit>
}
