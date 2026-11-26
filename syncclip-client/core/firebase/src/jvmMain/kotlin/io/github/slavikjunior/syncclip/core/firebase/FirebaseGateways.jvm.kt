package io.github.slavikjunior.syncclip.core.firebase

actual fun createFirebaseAuthGateway(): FirebaseAuthGateway = UnsupportedFirebaseAuthGateway()

actual fun createPushTokenSource(): PushTokenSource = UnsupportedPushTokenSource()
