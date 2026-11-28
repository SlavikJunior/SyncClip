plugins {
    id("convention.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // NavKey — публичный супертип маршрутов, поэтому зависимость в api:
            // api-модули фич реализуют его в своём ABI (INV-18).
            api(libs.androidx.navigation3.runtime)
        }
    }
}
