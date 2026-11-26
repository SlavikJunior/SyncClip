package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Desktop-заглушки: общий код получает типизированный отказ, а не краш. */
class UnsupportedFirebaseGatewaysTest {

    private val gateway = UnsupportedFirebaseAuthGateway()
    private val pushTokens = UnsupportedPushTokenSource()

    @Test
    fun `auth state is empty and user is null`() = runTest {
        assertNull(gateway.currentUser())
        assertNull(gateway.authState.firstOrNull())
    }

    @Test
    fun `sign in reports not supported`() = runTest {
        val result = gateway.signInWithEmail("user@example.com", "password123")
        assertEquals(
            FirebaseAuthResult.Failure(FirebaseAuthFailure.NOT_SUPPORTED),
            result,
        )
    }

    @Test
    fun `push tokens are unavailable on desktop`() = runTest {
        assertNull(pushTokens.currentToken())
        assertFalse(pushTokens.requestPermission())
    }
}
