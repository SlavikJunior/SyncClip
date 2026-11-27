package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state

import app.cash.turbine.test
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthFailureReason
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthResult
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit is ignored while password is too short`() = runTest {
        val viewModel = AuthViewModel(repository)
        viewModel.handleIntent(AuthIntent.EmailChanged("user@example.com"))
        viewModel.handleIntent(AuthIntent.PasswordChanged("123"))

        viewModel.handleIntent(AuthIntent.Submit)
        advanceUntilIdle()

        assertEquals(0, repository.signInCalls)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `successful sign in clears password and emits authenticated`() = runTest {
        val viewModel = AuthViewModel(repository)
        fillForm(viewModel)

        viewModel.effect.test {
            viewModel.handleIntent(AuthIntent.Submit)
            advanceUntilIdle()

            assertEquals(AuthEffect.Authenticated, awaitItem())
            cancelAndConsumeRemainingEvents()
        }

        assertFalse(viewModel.state.value.isSubmitting)
        assertEquals("", viewModel.state.value.password)
        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `failure keeps form and reports reason`() = runTest {
        repository.result = AuthResult.Failure(AuthFailureReason.INVALID_CREDENTIALS)
        val viewModel = AuthViewModel(repository)
        fillForm(viewModel)

        viewModel.effect.test {
            viewModel.handleIntent(AuthIntent.Submit)
            advanceUntilIdle()

            assertEquals(
                AuthEffect.ShowMessage(AuthFailureReason.INVALID_CREDENTIALS),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }

        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, viewModel.state.value.failure)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `toggle mode switches between sign in and sign up`() = runTest {
        val viewModel = AuthViewModel(repository)
        assertEquals(AuthMode.SIGN_IN, viewModel.state.value.mode)

        viewModel.handleIntent(AuthIntent.ToggleMode)
        assertEquals(AuthMode.SIGN_UP, viewModel.state.value.mode)

        viewModel.handleIntent(AuthIntent.ToggleMode)
        assertEquals(AuthMode.SIGN_IN, viewModel.state.value.mode)
    }

    @Test
    fun `editing email clears previous failure`() = runTest {
        repository.result = AuthResult.Failure(AuthFailureReason.UNKNOWN)
        val viewModel = AuthViewModel(repository)
        fillForm(viewModel)

        viewModel.handleIntent(AuthIntent.Submit)
        advanceUntilIdle()
        assertEquals(AuthFailureReason.UNKNOWN, viewModel.state.value.failure)

        viewModel.handleIntent(AuthIntent.EmailChanged("another@example.com"))

        assertNull(viewModel.state.value.failure)
    }

    private fun fillForm(viewModel: AuthViewModel) {
        viewModel.handleIntent(AuthIntent.EmailChanged("user@example.com"))
        viewModel.handleIntent(AuthIntent.PasswordChanged("password123"))
    }
}

private class FakeAuthRepository(
    var result: AuthResult = AuthResult.Success,
) : AuthRepository {

    var signInCalls: Int = 0

    override suspend fun signIn(email: String, password: String): AuthResult {
        signInCalls++
        return result
    }

    override suspend fun signUp(email: String, password: String): AuthResult = result

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): AuthResult = result

    override suspend fun signOut() = Unit

    override suspend fun registerPushToken() = Unit
}
