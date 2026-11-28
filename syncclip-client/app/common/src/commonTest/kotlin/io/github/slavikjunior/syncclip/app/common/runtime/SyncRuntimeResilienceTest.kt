package io.github.slavikjunior.syncclip.app.common.runtime

import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import io.github.slavikjunior.syncclip.feature.sync.api.LanTransportStarter
import io.github.slavikjunior.syncclip.feature.sync.api.SyncTransportStarter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Отказ одного фонового конвейера не должен выключать приложение до перезапуска.
 *
 * Захват буфера, LAN и WAN живут в `supervisorScope`, а упавший конвейер
 * поднимается заново: пользователь не увидит «приложение перестало
 * синхронизировать» без причины.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SyncRuntimeResilienceTest {

    private val session = FakeSession()
    private val lan = SilentLan()
    private val transport = SilentTransport()

    @Test
    fun `failed capture pipeline is restarted`() = runTest {
        val capture = FlakyCapture(failures = 2)
        val runtime = SyncRuntime(session, capture, lan, transport, restartDelayMillis = 1_000)
        runtime.start(backgroundScope)
        session.signIn()
        runCurrent()

        assertEquals(1, capture.starts)
        assertTrue(capture.crashed, "первый запуск должен упасть")

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, capture.starts)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(3, capture.starts)
        assertTrue(capture.healthy, "после двух падений конвейер должен работать")
    }

    @Test
    fun `crash of one pipeline does not stop the others`() = runTest {
        val capture = FlakyCapture(failures = Int.MAX_VALUE)
        val runtime = SyncRuntime(session, capture, lan, transport, restartDelayMillis = 1_000)
        runtime.start(backgroundScope)
        session.signIn()
        runCurrent()

        assertEquals(1, lan.starts)
        assertEquals(1, transport.starts)
        assertTrue(lan.active)
        assertTrue(transport.active)
    }

    @Test
    fun `normal completion is not restarted`() = runTest {
        val transport = CompletedTransport()
        val runtime = SyncRuntime(session, SilentCapture(), lan, transport, restartDelayMillis = 1_000)
        runtime.start(backgroundScope)
        session.signIn()
        runCurrent()

        // Штатное завершение (отзыв сессии) — не ошибка.
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, transport.starts)
    }

    @Test
    fun `sign out stops restarts`() = runTest {
        val capture = FlakyCapture(failures = Int.MAX_VALUE)
        val runtime = SyncRuntime(session, capture, lan, transport, restartDelayMillis = 1_000)
        runtime.start(backgroundScope)
        session.signIn()
        runCurrent()

        session.signOut()
        runCurrent()
        val startsAfterSignOut = capture.starts

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(startsAfterSignOut, capture.starts, "вне сессии конвейер не перезапускается")
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

    private class FlakyCapture(private var failures: Int) : ClipboardCaptureStarter {

        var starts = 0
        var crashed = false
        var healthy = false

        override suspend fun run() {
            starts++
            if (failures > 0) {
                failures--
                crashed = true
                error("clipboard platform is broken")
            }
            healthy = true
            awaitCancellation()
        }

        override suspend fun captureCurrent(): Boolean = false
        override suspend fun captureText(text: String): Boolean = false
    }

    private class SilentCapture : ClipboardCaptureStarter {
        override suspend fun run() = awaitCancellation()
        override suspend fun captureCurrent(): Boolean = false
        override suspend fun captureText(text: String): Boolean = false
    }

    private class SilentLan : LanTransportStarter {
        var starts = 0
        var active = false

        override suspend fun run() {
            starts++
            active = true
            awaitCancellation()
        }
    }

    private class SilentTransport : SyncTransportStarter {
        var starts = 0
        var active = false

        override suspend fun run() {
            starts++
            active = true
            awaitCancellation()
        }
    }

    private class CompletedTransport : SyncTransportStarter {
        var starts = 0

        override suspend fun run() {
            starts++
        }
    }
}
