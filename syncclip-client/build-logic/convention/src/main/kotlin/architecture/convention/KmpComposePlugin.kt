package architecture.convention

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Общая конфигурация Compose Multiplatform для feature-модулей (INV-24).
 *
 * Помимо библиотек `commonMain` подключает tooling превью на Android-runtime:
 * без него IDE не может отрендерить `@Preview` из `commonMain` в модуле с
 * плагином `com.android.kotlin.multiplatform.library`.
 */
class KmpComposePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.plugins.apply("convention.kmp.library")
        target.plugins.apply("org.jetbrains.compose")
        target.plugins.apply("org.jetbrains.kotlin.plugin.compose")

        val libs = target.libs
        COMMON_COMPOSE_LIBRARIES.forEach { name ->
            target.dependencies.add(
                "commonMainImplementation",
                libs.findLibrary(name).get(),
            )
        }
        // Android-runtime, а не debugImplementation: у плагина KMP-библиотеки
        // (AGP 9) нет variant-специфичных конфигураций зависимостей.
        target.dependencies.add(
            "androidRuntimeClasspath",
            libs.findLibrary("cmp-ui-tooling").get(),
        )
    }

    private companion object {
        val COMMON_COMPOSE_LIBRARIES = listOf(
            "cmp-runtime",
            "cmp-foundation",
            "cmp-material3",
            "cmp-material3-adaptive",
            "cmp-components-resources",
            "cmp-ui-tooling-preview",
            "koin-compose",
        )
    }
}
