plugins {
    id("convention.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Единственное место проекта, где видна библиотека Kache: наружу
            // модуль публикует только контракт `KeyValueCache` (INV-03, PROFILE).
            // implementation, а не api: фичи не должны даже импортировать Kache.
            implementation(libs.kache.core)
        }
    }
}
