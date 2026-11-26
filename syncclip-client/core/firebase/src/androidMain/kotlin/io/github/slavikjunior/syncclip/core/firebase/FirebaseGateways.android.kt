package io.github.slavikjunior.syncclip.core.firebase

actual fun createFirebaseAuthGateway(): FirebaseAuthGateway = AndroidFirebaseAuthGateway()

actual fun createPushTokenSource(): PushTokenSource = AndroidPushTokenSource()
