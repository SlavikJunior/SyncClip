package io.github.slavikjunior.syncclip.core.firebase

/**
 * Платформенные фабрики Firebase-инфраструктуры.
 *
 * Вызываются из DI (`core:di`), чтобы фичи не знали, каким SDK реализован
 * контракт (INV-03, INV-29).
 */
expect fun createFirebaseAuthGateway(): FirebaseAuthGateway

expect fun createPushTokenSource(): PushTokenSource
