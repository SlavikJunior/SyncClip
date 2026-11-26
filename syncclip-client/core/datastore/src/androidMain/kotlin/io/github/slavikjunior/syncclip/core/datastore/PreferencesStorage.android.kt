package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import android.os.Build
import okio.Path.Companion.toPath

actual fun createPreferencesDataStore(platformContext: Any?): DataStore<Preferences> {
    val context = requireNotNull(platformContext as? android.content.Context) {
        "Android требует Context для DataStore"
    }
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { context.filesDir.resolve(PREFERENCES_FILE_NAME).absolutePath.toPath() },
    )
}

actual fun platformOsType(): String = "Android"

actual fun platformDeviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android device"
