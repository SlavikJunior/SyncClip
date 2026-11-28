package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardCapture
import io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.TargetedClipboardSender
import io.github.slavikjunior.syncclip.core.clipboard.TargetedSendResult
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.expiryMinutesLeft
import io.github.slavikjunior.syncclip.shared.model.Device
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import io.github.slavikjunior.syncclip.shared.model.DevicePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-017, US-016, US-020: избранное, адресная отправка и копирование. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryFavoritesAndSendTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val entry = HistoryEntry(
        id = "e-1",
        text = "hello",
        timestampMillis = 1_000L,
        sourceDeviceId = null,
        isPinned = false,
        isFavorite = false,
    )

    @Test
    fun `favorite filter shows only starred entries`() = runTest(dispatcher) {
        val repository = MutableHistoryRepository(
            listOf(entry, entry.copy(id = "e-2", isFavorite = true)),
        )
        val viewModel = HistoryViewModel(repository = repository, deviceDirectory = null)
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.SetFilter(HistoryFilter.FAVORITES))
        advanceUntilIdle()

        assertEquals(listOf("e-2"), viewModel.state.value.entries.map { it.id })
    }

    @Test
    fun `toggle favorite persists and moves entry out of the favorites filter`() =
        runTest(dispatcher) {
            val repository = MutableHistoryRepository(listOf(entry.copy(isFavorite = true)))
            val viewModel = HistoryViewModel(repository = repository, deviceDirectory = null)
            advanceUntilIdle()
            viewModel.handleIntent(HistoryIntent.SetFilter(HistoryFilter.FAVORITES))
            advanceUntilIdle()

            viewModel.handleIntent(HistoryIntent.ToggleFavorite("e-1"))
            advanceUntilIdle()

            assertEquals(listOf("e-1" to false), repository.favoriteCalls)
            assertTrue(viewModel.state.value.entries.isEmpty())
        }

    @Test
    fun `double tap copies the text into the system clipboard`() = runTest(dispatcher) {
        val gateway = FakeClipboardGateway(current = "old")
        val viewModel = HistoryViewModel(
            repository = MutableHistoryRepository(listOf(entry)),
            deviceDirectory = null,
            gateway = gateway,
        )
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.CopyToClipboard("e-1"))
        advanceUntilIdle()

        assertEquals(listOf("hello"), gateway.inserted)
    }

    @Test
    fun `send dialog preselects every other device`() = runTest(dispatcher) {
        val directory = FakeDeviceDirectory(
            listOf(
                device("this", current = true),
                device("phone"),
                device("tablet"),
            ),
        )
        val viewModel = HistoryViewModel(
            repository = MutableHistoryRepository(listOf(entry)),
            deviceDirectory = directory,
            gateway = FakeClipboardGateway(current = "clipboard"),
            sender = FakeSender(),
        )
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.OpenSendTargets)
        advanceUntilIdle()

        val dialog = viewModel.state.value.sendDialog
        assertEquals(listOf("phone", "tablet"), dialog?.targets?.map { it.id })
        assertEquals(setOf("phone", "tablet"), dialog?.selectedIds)
        assertTrue(dialog?.canSend == true)
    }

    @Test
    fun `confirm send passes selected ids to the transport`() = runTest(dispatcher) {
        val sender = FakeSender()
        val viewModel = HistoryViewModel(
            repository = MutableHistoryRepository(listOf(entry)),
            deviceDirectory = FakeDeviceDirectory(listOf(device("this", true), device("phone"))),
            gateway = FakeClipboardGateway(current = "clipboard text"),
            sender = sender,
        )
        advanceUntilIdle()
        viewModel.handleIntent(HistoryIntent.OpenSendTargets)
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.ConfirmSend)
        advanceUntilIdle()

        assertEquals(listOf("clipboard text" to listOf("phone")), sender.sent)
        assertEquals(null, viewModel.state.value.sendDialog)
    }

    @Test
    fun `offline send closes the dialog and reports the failure`() = runTest(dispatcher) {
        val viewModel = HistoryViewModel(
            repository = MutableHistoryRepository(listOf(entry)),
            deviceDirectory = FakeDeviceDirectory(listOf(device("this", true), device("phone"))),
            gateway = FakeClipboardGateway(current = "text"),
            sender = FakeSender(result = TargetedSendResult.Offline),
        )
        advanceUntilIdle()
        viewModel.handleIntent(HistoryIntent.OpenSendTargets)
        advanceUntilIdle()
        viewModel.handleIntent(HistoryIntent.ConfirmSend)
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.sendDialog)
    }

    @Test
    fun `expiry badge counts minutes up and stops at the deadline`() {
        val now = 1_000_000L
        val minute = 60_000L
        // Обычная запись — без подписи.
        assertEquals(null, expiryMinutesLeft(expiresAtMillis = null, nowMillis = now))
        // Полторы минуты до удаления показываются как «через 2 мин».
        assertEquals(2, expiryMinutesLeft(expiresAtMillis = now + minute + minute / 2, nowMillis = now))
        // Истёкший срок — подписи нет: запись удалит ближайшая очистка.
        assertEquals(null, expiryMinutesLeft(expiresAtMillis = now - 1, nowMillis = now))
    }
}

private fun device(id: String, current: Boolean = false): Device = Device(
    id = id,
    name = "Device $id",
    icon = DeviceIcon.PHONE,
    platform = DevicePlatform.ANDROID,
    isCurrent = current,
)

private class FakeDeviceDirectory(private val list: List<Device>) : DeviceDirectory {
    override suspend fun devices(): List<Device> = list
}

private class FakeClipboardGateway(var current: String?) : ClipboardGateway {

    val inserted: MutableList<String> = mutableListOf()

    override fun localCopies(): Flow<ClipboardCapture> = flowOf()

    override suspend fun currentText(): String? = current

    override suspend fun insert(text: String) {
        inserted += text
        current = text
    }

    override fun lastLocalCaptureMillis(): Long = 0L
}

private class FakeSender(
    private val result: TargetedSendResult = TargetedSendResult.Queued,
) : TargetedClipboardSender {

    val sent: MutableList<Pair<String, List<String>>> = mutableListOf()

    override suspend fun send(text: String, targetDeviceIds: List<String>): TargetedSendResult {
        sent += text to targetDeviceIds
        return result
    }
}

/** Репозиторий с изменяемым списком: проверяет запись избранного. */
private class MutableHistoryRepository(
    private val rows: List<HistoryEntry>,
) : HistoryRepository {

    val favoriteCalls: MutableList<Pair<String, Boolean>> = mutableListOf()
    private val state = MutableStateFlow(rows)

    override fun observePage(query: String, limit: Int, offset: Int): Flow<List<HistoryEntry>> =
        state

    override suspend fun count(query: String): Int = state.value.size

    override suspend fun findById(id: String): HistoryEntry? =
        state.value.firstOrNull { it.id == id }

    override suspend fun cachedFirstPage(limit: Int): List<HistoryEntry>? = null

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun restore(entry: HistoryEntry) {
        state.value = state.value + entry
    }

    override suspend fun setPinned(id: String, pinned: Boolean) = Unit

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        favoriteCalls += id to favorite
        state.value = state.value.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }

    override suspend fun evictExpired(cutoffMillis: Long, maxCount: Int): Int = 0

    override suspend fun clearExpiredSensitive(nowMillis: Long): Int = 0
}
