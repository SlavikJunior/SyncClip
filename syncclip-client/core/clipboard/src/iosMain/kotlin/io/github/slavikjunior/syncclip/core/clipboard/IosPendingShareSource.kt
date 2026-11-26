package io.github.slavikjunior.syncclip.core.clipboard

import platform.Foundation.NSUserDefaults

/**
 * Очередь Share Extension в App Group.
 *
 * Расширение и приложение — разные процессы; общего хранилища, кроме App Group,
 * у них нет. `pendingShares` — неотправленные тексты (нет сессии/сети),
 * `sentShares` — уже отправленные, их приложение только добавляет в историю.
 */
internal class IosPendingShareSource : PendingShareSource {

    override fun take(): List<String> = drain(PENDING_KEY)

    override fun takeAlreadySent(): List<String> = drain(SENT_KEY)

    private fun drain(key: String): List<String> {
        val defaults = NSUserDefaults(suiteName = APP_GROUP) ?: return emptyList()
        val stored = defaults.arrayForKey(key) ?: return emptyList()
        val texts = stored.mapNotNull { it as? String }.filter { it.isNotBlank() }
        if (texts.isNotEmpty()) defaults.removeObjectForKey(key)
        return texts
    }

    private companion object {
        const val APP_GROUP = "group.io.github.slavikjunior.syncclip"
        const val PENDING_KEY = "pendingShares"
        const val SENT_KEY = "sentShares"
    }
}

public actual fun createPendingShareSource(platformContext: Any?): PendingShareSource =
    IosPendingShareSource()
