package io.github.slavikjunior.syncclip.feature.auth.impl.data.repository

import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthFailure
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.AuthHttpException
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthFailureReason
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Двухшаговый вход: Firebase → сервер → сессия → FCM. */
class FirebaseAuthRepositoryTest {

    private val gateway = FakeFirebaseAuthGateway()
    private val api = FakeAuthApi()
    private val sessionStore = FakeSessionStore()
    private val pushTokens = FakePushTokenSource()
    private val lockout = FakeAuthLockoutStore()

    private val repository = FirebaseAuthRepository(
        gateway = gateway,
        api = api,
        sessionStore = sessionStore,
        deviceIdentityStore = FakeDeviceIdentityStore(),
        pushTokens = pushTokens,
        lockout = lockout,
    )

    @Test
    fun `sign in exchanges firebase token and stores server session`() = runTest {
        val result = repository.signIn("user@example.com", "password123")

        assertEquals(AuthResult.Success, result)
        assertEquals("access-token", sessionStore.saved?.accessToken)
        assertEquals("server-user-1", sessionStore.saved?.userId)
        assertEquals("firebase-id-token", api.lastLogin?.idToken)
        assertEquals("device-1", api.lastLogin?.deviceId)
        assertEquals("Android", api.lastLogin?.osType)
    }

    @Test
    fun `sign in registers fcm token for the device`() = runTest {
        repository.signIn("user@example.com", "password123")

        assertEquals(1, api.registeredTokens.size)
        assertEquals("fcm-token", api.registeredTokens.single().fcmToken)
        assertEquals("device-1", api.registeredTokens.single().deviceId)
    }

    @Test
    fun `firebase failure maps to domain reason and skips server call`() = runTest {
        gateway.signInResult = firebaseFailure(FirebaseAuthFailure.INVALID_CREDENTIALS)

        val result = repository.signIn("user@example.com", "wrong")

        assertEquals(AuthResult.Failure(AuthFailureReason.INVALID_CREDENTIALS), result)
        assertEquals(null, api.lastLogin)
        assertEquals(null, sessionStore.saved)
    }

    @Test
    fun `missing id token fails without server call`() = runTest {
        gateway.token = null

        val result = repository.signIn("user@example.com", "password123")

        assertEquals(AuthResult.Failure(AuthFailureReason.UNKNOWN), result)
        assertEquals(null, api.lastLogin)
    }

    @Test
    fun `unverified email from server maps to email not verified`() = runTest {
        api.loginResult = Result.failure(AuthHttpException(statusCode = 403, errorCode = "email_not_verified"))

        val result = repository.signIn("user@example.com", "password123")

        assertEquals(AuthResult.Failure(AuthFailureReason.EMAIL_NOT_VERIFIED), result)
        assertEquals(null, sessionStore.saved)
    }

    @Test
    fun `sign out clears session and firebase state`() = runTest {
        repository.signIn("user@example.com", "password123")

        repository.signOut()

        assertTrue(gateway.signedOut)
        assertTrue(sessionStore.cleared)
        assertEquals(null, sessionStore.accessToken())
        assertTrue(lockout.cleared, "блокировка мастер-пароля не должна переходить к другому аккаунту")
    }

    @Test
    fun `push registration is skipped without session`() = runTest {
        repository.registerPushToken()

        assertTrue(api.registeredTokens.isEmpty())
    }

    @Test
    fun `push registration keeps last token for rotation`() = runTest {
        repository.signIn("user@example.com", "password123")
        pushTokens.token = "rotated-token"

        repository.registerPushToken()

        assertEquals("rotated-token", api.registeredTokens.last().fcmToken)
        assertNotNull(api.registeredTokens.last().deviceId)
    }

    @Test
    fun `desktop falls back to server login when firebase is unsupported`() = runTest {
        gateway.signInResult = firebaseFailure(FirebaseAuthFailure.NOT_SUPPORTED)

        val result = repository.signIn("desktop@example.com", "password123")

        assertEquals(AuthResult.Success, result)
        assertEquals("desktop@example.com", api.lastPasswordLogin?.email)
        assertEquals("device-1", api.lastPasswordLogin?.deviceId)
        assertEquals("server-user-1", sessionStore.saved?.userId)
    }

    @Test
    fun `desktop sign up falls back to server register`() = runTest {
        gateway.signInResult = firebaseFailure(FirebaseAuthFailure.NOT_SUPPORTED)

        val result = repository.signUp("new@example.com", "password123")

        assertEquals(AuthResult.Success, result)
        assertEquals("new@example.com", api.lastPasswordRegister?.email)
        assertEquals(null, api.lastPasswordLogin)
    }

    @Test
    fun `firebase failure other than unsupported does not hit server auth`() = runTest {
        gateway.signInResult = firebaseFailure(FirebaseAuthFailure.INVALID_CREDENTIALS)

        val result = repository.signIn("user@example.com", "wrong")

        assertEquals(AuthResult.Failure(AuthFailureReason.INVALID_CREDENTIALS), result)
        assertEquals(null, api.lastPasswordLogin)
    }

    @Test
    fun `server conflict maps to email already in use`() = runTest {
        gateway.signInResult = firebaseFailure(FirebaseAuthFailure.NOT_SUPPORTED)
        api.registerPasswordResult = Result.failure(
            AuthHttpException(statusCode = 409, errorCode = "email_already_registered"),
        )

        val result = repository.signUp("taken@example.com", "password123")

        assertEquals(AuthResult.Failure(AuthFailureReason.EMAIL_ALREADY_IN_USE), result)
        assertEquals(null, sessionStore.saved)
    }

    @Test
    fun `server validation error maps to invalid input`() = runTest {
        gateway.signInResult = firebaseFailure(FirebaseAuthFailure.NOT_SUPPORTED)
        api.loginPasswordResult = Result.failure(
            AuthHttpException(statusCode = 400, errorCode = "validation_error"),
        )

        val result = repository.signIn("bad@example.com", "x")

        assertEquals(AuthResult.Failure(AuthFailureReason.INVALID_INPUT), result)
    }
}

/** Блокировка мастер-пароля, которой управляет только тест. */
private class FakeAuthLockoutStore : io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore {

    private val values = kotlinx.coroutines.flow.MutableStateFlow(
        io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout(),
    )
    override val state: kotlinx.coroutines.flow.Flow<io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout> = values

    var cleared: Boolean = false
        private set

    override suspend fun current() = values.value

    override suspend fun recordAttempt(remaining: Int) {
        values.value = io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout(
            remainingAttempts = remaining,
        )
    }

    override suspend fun lockUntil(lockedUntilMillis: Long) {
        values.value = io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout(
            remainingAttempts = 0,
            lockedUntilMillis = lockedUntilMillis,
        )
    }

    override suspend fun clear() {
        cleared = true
        values.value = io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout()
    }
}
