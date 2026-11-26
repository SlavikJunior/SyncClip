package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/** На iOS автозапуска приложения нет. */
public actual fun createAutoStart(
    dataStore: DataStore<Preferences>,
    platformContext: Any?,
): AutoStart = NoopAutoStart
