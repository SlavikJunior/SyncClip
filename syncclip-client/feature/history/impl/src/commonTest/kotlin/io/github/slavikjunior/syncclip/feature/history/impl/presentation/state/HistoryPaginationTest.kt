package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/**
 * Постраничная загрузка истории при фильтре по типу.
 *
 * Фильтр скрывает часть строк страницы, поэтому offset нельзя считать по числу
 * видимых записей: иначе клиент бесконечно запрашивает одну и ту же страницу.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryPaginationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filtered list pages over rows instead of visible entries`() = runTest(dispatcher) {
        // 100 строк: первые 60 — ссылки, остальные — код.
        val rows = (1..100).map { index ->
            entry(
                id = "id-$index",
                text = if (index <= 60) "https://example.com/$index" else "fun main$index() {}",
            )
        }
        val repository = PagedHistoryRepository(rows)
        val viewModel = HistoryViewModel(repository = repository, deviceDirectory = null)
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.SetFilter(HistoryFilter.LINKS))
        advanceUntilIdle()
        assertEquals(50, viewModel.state.value.entries.size)

        // Смена фильтра перечитывает первую страницу — дальше считаем только
        // запросы следующей страницы.
        repository.requestedOffsets.clear()

        viewModel.handleIntent(HistoryIntent.LoadNextPage)
        advanceUntilIdle()
        assertEquals(60, viewModel.state.value.entries.size)

        // Дальше строк нет: повторные вызовы не должны читать ту же страницу.
        repeat(3) {
            viewModel.handleIntent(HistoryIntent.LoadNextPage)
            advanceUntilIdle()
        }

        assertEquals(listOf(50), repository.requestedOffsets)
        assertEquals(60, viewModel.state.value.entries.size)
        assertTrue(!viewModel.state.value.isLoadingMore)
    }

    @Test
    fun `short page ends the list`() = runTest(dispatcher) {
        val repository = PagedHistoryRepository((1..30).map { entry("id-$it", "text $it") })
        val viewModel = HistoryViewModel(repository = repository, deviceDirectory = null)
        advanceUntilIdle()

        repeat(3) {
            viewModel.handleIntent(HistoryIntent.LoadNextPage)
            advanceUntilIdle()
        }

        assertEquals(listOf(0), repository.requestedOffsets)
    }

    @Test
    fun `deleting the last row of a page keeps pagination consistent`() = runTest(dispatcher) {
        val rows = (1..60).map { entry("id-$it", "text $it") }
        val repository = PagedHistoryRepository(rows)
        val viewModel = HistoryViewModel(repository = repository, deviceDirectory = null)
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.Delete("id-1"))
        advanceUntilIdle()
        viewModel.handleIntent(HistoryIntent.LoadNextPage)
        advanceUntilIdle()

        assertEquals(listOf(0, 50), repository.requestedOffsets)
        assertEquals(59, viewModel.state.value.entries.size)
    }

    @Test
    fun `load more disappears when all rows are read`() = runTest(dispatcher) {
        val rows = (1..100).map { index ->
            entry(
                id = "id-$index",
                text = if (index <= 60) "https://example.com/$index" else "fun main$index() {}",
            )
        }
        val viewModel = HistoryViewModel(PagedHistoryRepository(rows), deviceDirectory = null)
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.SetFilter(HistoryFilter.LINKS))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hasMore, "после первой страницы есть что читать")

        viewModel.handleIntent(HistoryIntent.LoadNextPage)
        advanceUntilIdle()

        // Видимых записей 60, строк — 100: кнопка по `entries.size` висела бы
        // вечно, хотя читать больше нечего.
        assertEquals(60, viewModel.state.value.entries.size)
        assertTrue(!viewModel.state.value.hasMore, "кнопка «загрузить ещё» должна исчезнуть")
    }

    @Test
    fun `failed page load leaves the list retryable`() = runTest(dispatcher) {
        val rows = (1..120).map { entry("id-$it", "text $it") }
        val repository = PagedHistoryRepository(rows, failFromOffset = 50)
        val viewModel = HistoryViewModel(repository, deviceDirectory = null)
        advanceUntilIdle()

        viewModel.handleIntent(HistoryIntent.LoadNextPage)
        advanceUntilIdle()

        // Исключение не выходит в viewModelScope и не оставляет «грузится».
        assertTrue(!viewModel.state.value.isLoadingMore)
        assertEquals(50, viewModel.state.value.entries.size)
        assertTrue(viewModel.state.value.hasMore, "после сбоя чтения можно попробовать снова")
    }

    private fun entry(id: String, text: String) = HistoryEntry(
        id = id,
        text = text,
        timestampMillis = 1_000_000L - id.drop(3).toLong(),
        sourceDeviceId = null,
        isPinned = false,
    )
}

/** Репозиторий с настоящей пагинацией: запоминает запрошенные offset. */
private class PagedHistoryRepository(
    private val rows: List<HistoryEntry>,
    /** Offset, с которого чтение падает (сбой БД), — для проверки retry. */
    private val failFromOffset: Int? = null,
) : HistoryRepository {

    val requestedOffsets: MutableList<Int> = mutableListOf()

    override fun observePage(query: String, limit: Int, offset: Int): Flow<List<HistoryEntry>> {
        requestedOffsets += offset
        if (failFromOffset != null && offset >= failFromOffset) error("disk is full")
        return flowOf(rows.drop(offset).take(limit))
    }

    override suspend fun count(query: String): Int = rows.size

    override suspend fun findById(id: String): HistoryEntry? = rows.firstOrNull { it.id == id }

    override suspend fun cachedFirstPage(limit: Int): List<HistoryEntry>? = null

    override suspend fun delete(id: String) = Unit

    override suspend fun restore(entry: HistoryEntry) = Unit

    override suspend fun setPinned(id: String, pinned: Boolean) = Unit

    override suspend fun evictExpired(cutoffMillis: Long, maxCount: Int): Int = 0
}
