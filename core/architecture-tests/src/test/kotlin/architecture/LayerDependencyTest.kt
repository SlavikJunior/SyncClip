package architecture

import com.lemonappdev.konsist.api.ext.list.*
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * INV-04 — domain не зависит ни от чего в проекте.
 * INV-10 — однонаправленный поток данных.
 *
 * Направления зависимостей между слоями:
 *   presentation → domain
 *   data        → domain
 *   domain      → ничего
 *
 * Проверка сделана через импорты, а не через `assertArchitecture`:
 * тот требует наличия всех трёх слоёв в проекте и падает с исключением,
 * если слой ещё не создан. Здесь проверки мягкие и дают понятный отчёт.
 */
class LayerDependencyTest {

    private val productionScope = applicationScope()

    private val domainFiles = productionScope.files.inLayer(DOMAIN_PACKAGE)
    private val dataFiles = productionScope.files.inLayer(DATA_PACKAGE)
    private val presentationFiles = productionScope.files.inLayer(PRESENTATION_PACKAGE)

    @Test
    fun `domain does not depend on data or presentation`() {
        Coverage.report("LayerDependencyTest.domainToLayers", "файлы domain", domainFiles.size)

        domainFiles.assertTrue { file ->
            file.imports.none { import ->
                import.name.contains(".data.") || import.name.contains(".presentation.")
            }
        }
    }

    @Test
    fun `data does not depend on presentation`() {
        Coverage.report("LayerDependencyTest.dataToPresentation", "файлы data", dataFiles.size)
        dataFiles.assertTrue { file ->
            file.imports.none { import -> import.name.contains(".presentation.") }
        }
    }

    @Test
    fun `presentation does not depend on data directly`() {
        Coverage.report("LayerDependencyTest.presentationToData", "файлы presentation", presentationFiles.size)
        presentationFiles.assertTrue { file ->
            file.imports.none { import -> import.name.contains(".data.") }
        }
    }

    @Test
    fun `domain does not depend on other project modules`() {
        Coverage.report("LayerDependencyTest.domainToModules", "файлы domain", domainFiles.size)
        domainFiles.assertTrue { file ->
            file.imports.none { import ->
                import.name.startsWith(BASE_PACKAGE) &&
                    !import.name.contains(".domain.")
            }
        }
    }

    @Test
    fun `data does not depend on other feature modules`() {
        Coverage.report("LayerDependencyTest.dataToOtherFeatures", "файлы data", dataFiles.size)
        dataFiles.assertTrue { file ->
            val ownFeature = featureNameOf(file.packagee?.name)
            file.imports.none { import ->
                val importedFeature = featureNameOf(import.name)
                importedFeature != null &&
                    importedFeature != ownFeature &&
                    !import.name.contains(".domain.")
            }
        }
    }
}

/**
 * Имя фичи по имени пакета или импорта: `...feature.<name>...` → `<name>`.
 *
 * Нужно, чтобы отличать зависимость от чужой фичи от законной: слой `data`
 * реализует контракт (`api`) своей же фичи, и это не нарушение INV-01.
 */
private fun featureNameOf(path: String?): String? {
    if (path == null) return null
    val marker = "feature."
    val index = path.indexOf(marker)
    if (index < 0) return null
    return path.substring(index + marker.length)
        .substringBefore('.')
        .takeIf { it.isNotEmpty() }
}
