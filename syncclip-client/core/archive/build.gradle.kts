plugins {
    id("convention.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Экспорт и импорт истории (US-018, US-024).
 *
 * Форматы и разбор вынесены из фичи истории в отдельный инфраструктурный модуль:
 * архивированием пользуется экран настроек, а фичи не могут зависеть друг от
 * друга (INV-01). Модуль работает с Room через `:core:database` и не знает про
 * Compose и DI-контейнер.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:database"))
            implementation(project(":shared:model"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
