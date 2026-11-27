package io.github.slavikjunior.syncclip.feature.auth.impl.data.repository

import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.AuthApi
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.AuthTokensDto
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.FcmRegisterRequestDto
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.FirebaseLoginRequestDto
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.PasswordAuthRequestDto

internal class FakeAuthApi(
    var loginResult: Result<AuthTokensDto> = Result.success(
        AuthTokensDto(
            userId = "server-user-1",
            accessToken = "access-token",
            refreshToken = "refresh-token",
        ),
    ),
) : AuthApi {

    var lastLogin: FirebaseLoginRequestDto? = null
    var registeredTokens: MutableList<FcmRegisterRequestDto> = mutableListOf()

    var loginPasswordResult: Result<AuthTokensDto> = loginResult
    var registerPasswordResult: Result<AuthTokensDto> = loginResult
    var lastPasswordLogin: PasswordAuthRequestDto? = null
    var lastPasswordRegister: PasswordAuthRequestDto? = null

    override suspend fun login(request: FirebaseLoginRequestDto): Result<AuthTokensDto> {
        lastLogin = request
        return loginResult
    }

    override suspend fun loginPassword(request: PasswordAuthRequestDto): Result<AuthTokensDto> {
        lastPasswordLogin = request
        return loginPasswordResult
    }

    override suspend fun registerPassword(request: PasswordAuthRequestDto): Result<AuthTokensDto> {
        lastPasswordRegister = request
        return registerPasswordResult
    }

    override suspend fun registerFcmToken(request: FcmRegisterRequestDto): Result<Unit> {
        registeredTokens += request
        return Result.success(Unit)
    }
}
