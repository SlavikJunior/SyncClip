package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import platform.Foundation.NSHomeDirectory
import platform.UIKit.UIDevice

actual fun createPreferencesDataStore(platformContext: Any?): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { (NSHomeDirectory() + "/Documents/" + PREFERENCES_FILE_NAME).toPath() },
    )

actual fun platformOsType(): String = "iOS"

actual fun platformDeviceName(): String =
    UIDevice.currentDevice.name.takeIf { it.isNotBlank() } ?: "iOS device"
