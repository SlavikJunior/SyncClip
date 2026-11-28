plugins {
    id("convention.kmp.library")
}

/**
 * Платформенные возможности, которых нет в общем коде: системный диалог файлов,
 * биометрическая блокировка и локальные уведомления о входящих копиях.
 *
 * Модуль содержит только контракты и заглушки: реализации живут в точках входа
 * (`:app:androidApp`, `:app:iosApp`, `:app:desktopApp`), потому что им нужны
 * Activity, UIViewController и окно Desktop. Фичи видят интерфейсы, а не
 * платформенные API (INV-03, INV-29).
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
