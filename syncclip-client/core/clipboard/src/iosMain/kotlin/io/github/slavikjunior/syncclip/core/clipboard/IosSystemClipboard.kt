package io.github.slavikjunior.syncclip.core.clipboard

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import platform.UIKit.UIPasteboard

/**
 * Буфер iOS через [UIPasteboard].
 *
 * Публичного уведомления об изменении буфера другим приложением нет, поэтому
 * используется опрос `changeCount`: он меняется при любой записи в буфер.
 *
 * Ограничение ОС (iOS 14+): чтение буфера другим приложением показывает
 * пользователю системную плашку «вставлено из …». Опрос запускается только при
 * активном приложении; фоновое чтение без действия пользователя невозможно.
 */
internal class IosSystemClipboard : SystemClipboard {

    override fun changes(): Flow<ClipboardChange> = flow {
        // Содержимое, которое уже было в буфере до старта, не считается копией.
        var knownChangeCount = pasteboard().changeCount
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MILLIS)
            val clipboard = pasteboard()
            val changeCount = clipboard.changeCount
            if (changeCount == knownChangeCount) continue
            knownChangeCount = changeCount
            val text = clipboard.string?.takeIf { it.isNotBlank() } ?: continue
            emit(
                ClipboardChange(
                    text = text,
                    changedAtMillis = EpochClock.System.nowMillis(),
                ),
            )
        }
    }

    override suspend fun read(): String? =
        pasteboard().string?.takeIf { it.isNotBlank() }

    override suspend fun write(text: String) {
        pasteboard().string = text
    }

    private fun pasteboard(): UIPasteboard = UIPasteboard.generalPasteboard

    private companion object {
        const val POLL_INTERVAL_MILLIS = 400L
    }
}

public actual fun createSystemClipboard(platformContext: Any?): SystemClipboard = IosSystemClipboard()
