package io.github.slavikjunior.syncclip.core.clipboard

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Буфер Desktop через AWT.
 *
 * `FlavorListener` на Linux и Windows приходит не на каждое изменение, поэтому
 * используется опрос содержимого: он одинаково работает на macOS, Windows и
 * Linux. Системный буфер не потокобезопасен — чтение и запись сериализованы.
 */
internal class AwtSystemClipboard : SystemClipboard {

    private val clipboard: Clipboard by lazy { Toolkit.getDefaultToolkit().systemClipboard }

    private val lock = Any()

    override fun changes(): Flow<ClipboardChange> = flow {
        // Содержимое, которое уже было в буфере до старта, не считается копией.
        var knownText = readText()
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MILLIS)
            val text = readText() ?: continue
            if (text == knownText) continue
            knownText = text
            emit(
                ClipboardChange(
                    text = text,
                    changedAtMillis = EpochClock.System.nowMillis(),
                ),
            )
        }
    }

    override suspend fun read(): String? = readText()

    override suspend fun write(text: String) {
        synchronized(lock) {
            clipboard.setContents(StringSelection(text), null)
        }
    }

    private fun readText(): String? = synchronized(lock) {
        runCatching {
            val contents = clipboard.getContents(null) ?: return@runCatching null
            if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return@runCatching null
            }
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 300L
    }
}

public actual fun createSystemClipboard(platformContext: Any?): SystemClipboard = AwtSystemClipboard()
