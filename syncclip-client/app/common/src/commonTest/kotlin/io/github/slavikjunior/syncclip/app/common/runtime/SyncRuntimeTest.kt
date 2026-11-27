package io.github.slavikjunior.syncclip.app.common.runtime

import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import io.github.slavikjunior.syncclip.feature.sync.api.LanTransportStarter
import io.github.slavikjunior.syncclip.feature.sync.api.SyncTransportStarter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runtime стартует три конвейера один раз на сессию и идемпотентен.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SyncRuntimeTest {

    private val session = FakeSession()
    private val capture = CountingCapture()
    private val lan = CountingLan()
    private val transport = CountingTransport()

    @Test
    fun `pipelines start once after sign in`() = runTest {
        val runtime = SyncRuntime(session, capture, lan, transport)
        runtime.start(backgroundScope)
        runtime.start(backgroundScope)

        session.signIn()
        runCurrent()

        assertEquals(1, capture.starts)
        assertEquals(1, lan.starts)
        assertEquals(1, transport.starts)
    }

    @Test
    fun `sign out cancels pipelines and new sign in restarts them`() = runTest {
        val runtime = SyncRuntime(session, capture, lan, transport)
        runtime.start(backgroundScope)
        session.signIn()
        runCurrent()

        session.signOut()
        runCurrent()
        assertEquals(0, capture.active)
        assertEquals(0, lan.active)

        session.signIn()
        runCurrent()
        assertEquals(2, capture.starts)
    }

    private class FakeSession : SessionStore {

        private val mutableState = MutableStateFlow<SessionState>(SessionState.SignedOut)
        override val state: StateFlow<SessionState> = mutableState

        fun signIn() {
            mutableState.value = SessionState.SignedIn(
                SessionSnapshot(userId = "user", accessToken = "a", refreshToken = "r"),
            )
        }

        fun signOut() {
            mutableState.value = SessionState.SignedOut
        }

        override suspend fun refresh() = Unit
        override suspend fun save(session: SessionSnapshot) = Unit
        override suspend fun updateTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun clear() = Unit
        override fun accessToken(): String? = null
    }

    private class CountingCapture : ClipboardCaptureStarter {
        var starts = 0
        var active = 0

        override suspend fun run() {
            starts++
            active++
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                active--
            }
        }

        override suspend fun captureCurrent(): Boolean = false
        override suspend fun captureText(text: String): Boolean = false
    }

    private class CountingLan : LanTransportStarter {
        var starts = 0
        var active = 0

        override suspend fun run() {
            starts++
            active++
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                active--
            }
        }
    }

    private class CountingTransport : SyncTransportStarter {
        var starts = 0

        override suspend fun run() {
            starts++
            kotlinx.coroutines.awaitCancellation()
        }
    }
}
