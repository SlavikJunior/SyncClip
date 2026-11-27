package io.github.slavikjunior.syncclip.core.clipboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Реализация [ClipboardGateway] поверх платформенного буфера.
 *
 * Правила (в порядке проверки):
 * 1. пустой текст не синхронизируется;
 * 2. текст больше [maxTextBytes] отбрасывается (NFR: 512 КиБ);
 * 3. событие, совпавшее с собственной программной вставкой в пределах
 *    [echoWindowMillis], — это эхо, а не копирование пользователя (FR-2.7);
 * 4. повтор уже принятого текста не создаёт новое событие.
 *
 * Мьютекс нужен потому, что [insert] и системный callback приходят из разных
 * потоков: метка вставки должна быть видна проверке эха атомарно.
 */
internal class ObservedClipboardGateway(
    private val systemClipboard: SystemClipboard,
    private val clock: EpochClock,
    private val echoWindowMillis: Long,
    private val maxTextBytes: Int,
) : ClipboardGateway {

    private val mutex = Mutex()
    private var lastAcceptedText: String? = null

    /** `StateFlow` вместо `var`: значение читается из другого потока без гонки. */
    private val lastCaptureMillis = MutableStateFlow(0L)
    private var injectedText: String? = null
    private var injectedAtMillis: Long = 0L

    override fun localCopies(): Flow<ClipboardCapture> =
        systemClipboard.changes().mapNotNull { change -> accept(change) }

    override suspend fun currentText(): String? =
        systemClipboard.read()?.takeIf { it.isNotBlank() }

    override suspend fun insert(text: String) {
        if (text.isBlank()) return
        val now = clock.nowMillis()
        mutex.withLock {
            injectedText = text
            injectedAtMillis = now
        }
        try {
            systemClipboard.write(text)
        } catch (failure: Throwable) {
            // Запись не удалась — буфера не изменилось, и метки эха быть не
            // должно: иначе следующее РЕАЛЬНОЕ копирование того же текста
            // пользователем было бы отброшено как собственное эхо.
            mutex.withLock {
                injectedText = null
                injectedAtMillis = 0L
            }
            throw failure
        }
    }

    override fun lastLocalCaptureMillis(): Long = lastCaptureMillis.value

    private suspend fun accept(change: ClipboardChange): ClipboardCapture? = mutex.withLock {
        val text = change.text
        if (text.isBlank()) return@withLock null
        if (ClipboardLimits.utf8Size(text) > maxTextBytes) return@withLock null

        val now = clock.nowMillis()
        if (isOwnInjection(text, now)) {
            // Метка одноразовая: следующее такое же событие уже может быть
            // осознанным копированием пользователя.
            injectedText = null
            return@withLock null
        }
        if (lastAcceptedText == text) return@withLock null

        lastAcceptedText = text
        lastCaptureMillis.value = now
        ClipboardCapture(text = text, capturedAtMillis = now)
    }

    private fun isOwnInjection(text: String, nowMillis: Long): Boolean {
        val injected = injectedText ?: return false
        if (injected != text) return false
        val elapsed = nowMillis - injectedAtMillis
        // Отрицательная разница — часы сдвинулись назад; трактуем как эхо.
        return elapsed <= echoWindowMillis
    }
}
