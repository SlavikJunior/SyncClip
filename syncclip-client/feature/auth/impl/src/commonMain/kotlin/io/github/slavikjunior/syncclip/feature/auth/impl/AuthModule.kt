package io.github.slavikjunior.syncclip.feature.auth.impl

import io.github.slavikjunior.syncclip.core.datastore.SessionRefresher
import io.github.slavikjunior.syncclip.feature.auth.api.PushTokenRegistrar
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.AuthApi
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.KtorAuthApi
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.KtorSessionRefresher
import io.github.slavikjunior.syncclip.feature.auth.impl.data.repository.AuthPushTokenRegistrar
import io.github.slavikjunior.syncclip.feature.auth.impl.data.repository.FirebaseAuthRepository
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.repository.AuthRepository
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state.AuthViewModel
import org.koin.dsl.module

/**
 * Публичный DI-контракт фичи.
 *
 * Зависимости приходят из `core:di` (Firebase, DataStore, HTTP-клиент) —
 * фича не создаёт их сама.
 */
public val authModule = module {
    single<AuthApi> { KtorAuthApi(get()) }
    single<AuthRepository> {
        FirebaseAuthRepository(
            gateway = get(),
            api = get(),
            sessionStore = get(),
            deviceIdentityStore = get(),
            pushTokens = get(),
            lockout = getOrNull(),
        )
    }
    single<PushTokenRegistrar> { AuthPushTokenRegistrar(get()) }
    single<SessionRefresher> { KtorSessionRefresher(client = get(), session = get()) }
    // ViewModel создаёт контейнер: иначе viewModelScope не отменяется (INV-08).
    factory { AuthViewModel(get()) }
}
