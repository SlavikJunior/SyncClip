package io.github.slavikjunior.syncclip.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Буфер Android через [ClipboardManager].
 *
 * `addPrimaryClipChangedListener` обязан вызываться на потоке с Looper, поэтому
 * подписка и отписка идут через главный [Handler], а события приходят в него же.
 *
 * Ограничение ОС (Android 10+): читать буфер может только приложение с фокусом
 * или IME по умолчанию, поэтому захват работает, пока приложение на экране.
 * Обход через прозрачные оверлеи и Accessibility запрещён паспортом (§5.4).
 */
internal class AndroidSystemClipboard(
    private val context: Context,
) : SystemClipboard {

    private val manager: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun changes(): Flow<ClipboardChange> = callbackFlow {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val text = readClipText() ?: return@OnPrimaryClipChangedListener
            trySend(
                ClipboardChange(
                    text = text,
                    changedAtMillis = EpochClock.System.nowMillis(),
                ),
            )
        }
        mainHandler.post { manager.addPrimaryClipChangedListener(listener) }
        awaitClose {
            mainHandler.post { manager.removePrimaryClipChangedListener(listener) }
        }
    }

    override suspend fun read(): String? = readClipText()

    override suspend fun write(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    private fun readClipText(): String? = runCatching {
        // Android 10+ бросает SecurityException, если приложение потеряло фокус
        // между подпиской и чтением: это не ошибка программиста, а штатное
        // ограничение ОС — считаем, что текста нет.
        val clip = manager.primaryClip ?: return@runCatching null
        if (clip.itemCount == 0) return@runCatching null
        val item = clip.getItemAt(0) ?: return@runCatching null
        item.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private companion object {
        const val CLIP_LABEL = "SyncClip"
    }
}

public actual fun createSystemClipboard(platformContext: Any?): SystemClipboard {
    val context = platformContext as? Context
        ?: error("Android требует Context для доступа к буферу обмена")
    return AndroidSystemClipboard(context.applicationContext)
}
