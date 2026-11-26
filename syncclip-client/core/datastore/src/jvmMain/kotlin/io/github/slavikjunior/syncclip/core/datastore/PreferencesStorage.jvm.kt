package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File

actual fun createPreferencesDataStore(platformContext: Any?): DataStore<Preferences> {
    val directory = File(System.getProperty("user.home"), ".syncclip").apply { mkdirs() }
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { directory.resolve(PREFERENCES_FILE_NAME).absolutePath.toPath() },
    )
}

actual fun platformOsType(): String {
    val os = System.getProperty("os.name").orEmpty()
    return when {
        os.contains("Mac", ignoreCase = true) -> "macOS"
        os.contains("Windows", ignoreCase = true) -> "Windows"
        else -> "Linux"
    }
}

actual fun platformDeviceName(): String =
    System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.name").orEmpty().ifBlank { "Desktop" }
