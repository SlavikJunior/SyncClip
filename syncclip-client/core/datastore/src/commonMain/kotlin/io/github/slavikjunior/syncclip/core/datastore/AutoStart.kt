package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Автозапуск при входе в ОС (FR-3.1, Desktop).
 *
 * Android/iOS не поддерживают понятие «автозапуск десктопного приложения» и
 * возвращают [NoopAutoStart]; на Desktop запись создаётся в
 * LaunchAgents (macOS), `~/.config/autostart` (Linux) или реестре (Windows).
 */
public interface AutoStart {

    /** `false` — приложение запущено не из упакованной сборки, автозапуск недоступен. */
    public val isSupported: Boolean

    public suspend fun isEnabled(): Boolean

    /** @return `true`, если состояние удалось применить. */
    public suspend fun setEnabled(enabled: Boolean): Boolean
}

internal object NoopAutoStart : AutoStart {
    override val isSupported: Boolean = false
    override suspend fun isEnabled(): Boolean = false
    override suspend fun setEnabled(enabled: Boolean): Boolean = false
}

public expect fun createAutoStart(
    dataStore: DataStore<Preferences>,
    platformContext: Any? = null,
): AutoStart
