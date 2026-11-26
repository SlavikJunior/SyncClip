package io.github.slavikjunior.syncclip.core.firebase

actual fun createFirebaseAuthGateway(): FirebaseAuthGateway = IosFirebaseAuthGateway()

actual fun createPushTokenSource(): PushTokenSource = IosPushTokenSource()
