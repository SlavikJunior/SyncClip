package io.github.slavikjunior.syncclip.feature.auth.impl.data.repository

import io.github.slavikjunior.syncclip.feature.auth.api.PushTokenRegistrar
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.repository.AuthRepository

/** Адаптер доменного репозитория к публичному контракту `api`. */
internal class AuthPushTokenRegistrar(
    private val repository: AuthRepository,
) : PushTokenRegistrar {

    override suspend fun registerCurrentToken() {
        repository.registerPushToken()
    }
}
