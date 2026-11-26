package io.github.slavikjunior.syncclip.core.di

import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthGateway
import io.github.slavikjunior.syncclip.core.firebase.PushTokenSource
import io.github.slavikjunior.syncclip.core.firebase.createFirebaseAuthGateway
import io.github.slavikjunior.syncclip.core.firebase.createPushTokenSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Firebase-инфраструктура: авторизация и FCM-токены.
 *
 * Конкретный SDK выбирает платформа (Play services / Swift-мост / заглушка),
 * фичи видят только контракты.
 */
public fun firebaseModule(): Module = module {
    single<FirebaseAuthGateway> { createFirebaseAuthGateway() }
    single<PushTokenSource> { createPushTokenSource() }
}
