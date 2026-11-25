package architecture

import com.lemonappdev.konsist.api.ext.list.*
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Проверки раскладки, не покрытые остальными тестами канона.
 *
 * Правила общие для всех проектов: Composable — в `ui`/`impl`/`designsystem`,
 * в `api`-модулях Composable нет, репозиторий — в `impl` или `api`, держатель
 * состояния с суффиксом `Store` — в `impl`. Последнее правило действует только
 * там, где такой суффикс используется (например, MVIKotlin), и в остальных
 * проектах ничего не проверяет.
 */
class ProjectLayoutTest {

    private val productionScope = applicationScope()
    private val composables = productionScope.functions()
        .withAnnotationOf(androidx.compose.runtime.Composable::class)

    @Test
    fun `composable functions reside in ui, impl or designsystem packages`() {
        Coverage.report("ProjectLayoutTest.uiPackages", "Composable-функции", composables.size)

        composables.assertTrue { function ->
            function.resideInPackage("..ui..") ||
                function.resideInPackage("..impl..") ||
                function.resideInPackage("..designsystem..")
        }
    }

    @Test
    fun `api modules do not contain composable functions`() {
        Coverage.report("ProjectLayoutTest.apiPackages", "Composable-функции", composables.size)
        composables.assertTrue { function ->
            function.resideOutsidePackage("..api..")
        }
    }

    @Test
    fun `repository classes reside in impl or api packages`() {
        val repositories = productionScope.classes().withNameEndingWith("Repository")
        Coverage.report("ProjectLayoutTest.repositoryPackages", "классы *Repository", repositories.size)
        repositories.assertTrue { declaration ->
            declaration.resideInPackage("..impl..") || declaration.resideInPackage("..api..")
        }
    }

    @Test
    fun `store classes reside in impl packages`() {
        val stores = productionScope.classes().withNameEndingWith("Store")
        Coverage.report("ProjectLayoutTest.storePackages", "классы *Store", stores.size)
        stores.assertTrue { declaration -> declaration.resideInPackage("..impl..") }
    }
}
