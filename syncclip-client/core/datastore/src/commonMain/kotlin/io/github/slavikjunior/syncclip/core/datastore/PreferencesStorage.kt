package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/** Единое имя файла настроек на всех платформах. */
internal const val PREFERENCES_FILE_NAME = "syncclip.preferences_pb"

/**
 * Фабрика DataStore.
 *
 * [platformContext] — Android `Context` (нужен `filesDir`); на iOS и Desktop
 * передаётся `null`, путь строится от домашнего каталога приложения.
 */
expect fun createPreferencesDataStore(platformContext: Any? = null): DataStore<Preferences>

/** `osType` для API сервера: Android, iOS, macOS, Windows, Linux. */
expect fun platformOsType(): String

/** Человекочитаемое имя устройства по умолчанию (сервер ограничивает 255 символами). */
expect fun platformDeviceName(): String
