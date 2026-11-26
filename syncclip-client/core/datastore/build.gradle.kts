plugins {
    id("convention.kmp.library")
}

/**
 * Локальные настройки и сессия (DataStore Preferences, KMP).
 *
 * Android получает `Context` через composition root, iOS и Desktop используют
 * каталог приложения (см. `createPreferencesDataStore`).
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:crypto"))
            api(libs.androidx.datastore.preferences)
        }
    }
}
