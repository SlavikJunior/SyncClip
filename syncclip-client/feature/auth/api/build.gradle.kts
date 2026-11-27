plugins {
    id("convention.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    // Публичная поверхность api-модуля проверяется компилятором (INV-26).
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:model"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
