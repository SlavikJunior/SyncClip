package architecture

import com.lemonappdev.konsist.api.ext.list.*
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * INV-25 — суффиксы имён соответствуют своей роли и лежат в правильном слое.
 *
 * Проверки намеренно мягкие: они ловят очевидные ошибки, а не спорные случаи.
 */
class NamingConventionTest {

    private val productionScope = applicationScope()

    @Test
    fun `use cases reside in domain layer`() {

        val useCases = productionScope.classes().withNameEndingWith(Suffixes.USE_CASE)
        Coverage.report("NamingConventionTest.useCasePackages", "классы *UseCase", useCases.size)
        useCases.assertTrue { it.resideInPackage(DOMAIN_PACKAGE) }
    }

    @Test
    fun `dtos reside in data layer`() {
        val dtos = productionScope.classes().withNameEndingWith(Suffixes.DTO)
        Coverage.report("NamingConventionTest.dtoPackages", "классы *Dto", dtos.size)
        dtos.assertTrue { it.resideInPackage(DATA_PACKAGE) }
    }

    @Test
    fun `entities reside in data layer`() {
        val entities = productionScope.classes().withNameEndingWith(Suffixes.ENTITY)
        Coverage.report("NamingConventionTest.entityPackages", "классы *Entity", entities.size)
        entities.assertTrue {
                // Слой данных — это не только пакет `..data..`: в проектах с
                // выделенными модулями (`core:database`, `core:local`) сущности
                // лежат в самом модуле. Все три варианта — один и тот же слой.
                it.resideInPackage(DATA_PACKAGE) ||
                    it.resideInPackage("..database..") ||
                    it.resideInPackage("..local..")
            }
    }

    @Test
    fun `ui state event and effect reside in presentation layer`() {
        val uiContracts = productionScope.classesAndInterfacesAndObjects()
            .withNameEndingWith(Suffixes.UI_STATE, Suffixes.UI_EVENT, Suffixes.UI_EFFECT, Suffixes.UI_INTENT)
        Coverage.report("NamingConventionTest.uiContractPackages", "типы *UiState, *UiEvent, *UiIntent, *UiEffect", uiContracts.size)
        uiContracts.assertTrue {
            it.resideInPackage(PRESENTATION_PACKAGE) || it.resideInPackage("..state..")
        }
    }

    @Test
    fun `repository contracts reside in domain or api package`() {
        val contracts = productionScope.interfaces().withNameEndingWith(Suffixes.REPOSITORY)
        Coverage.report("NamingConventionTest.repositoryPackages", "интерфейсы *Repository", contracts.size)
        contracts.assertTrue {
                it.resideInPackage(DOMAIN_PACKAGE) ||
                    it.resideInPackage("..repository..") ||
                    it.resideInPackage("..api..")
            }
    }

    @Test
    fun `file name matches the primary type name`() {
        Coverage.report("NamingConventionTest.primaryTypeName", "прикладные Kotlin-файлы", productionScope.files.size)
        productionScope.files.assertTrue { file ->
            // Интерфейсы обязательны в этом списке: файл с `sealed interface Node`
            // иначе считается «не имеющим совпадающего типа», хотя имя совпадает.
            val types = file.classes().map { it.name } +
                file.interfaces().map { it.name } +
                file.objects().map { it.name }
            // Имя файла в Konsist — без расширения.
            types.isEmpty() || types.any { type -> file.name == type }
        }
    }
}
