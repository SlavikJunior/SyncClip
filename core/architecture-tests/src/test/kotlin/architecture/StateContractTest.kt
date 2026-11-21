package architecture

import com.lemonappdev.konsist.api.ext.list.*
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * INV-08 — один StateFlow<UiState> на экран, мутабельный источник не наружу.
 * INV-09 — только атомарная мутация состояния.
 * INV-15 — на Android только collectAsStateWithLifecycle().
 *
 * Область проверки — держатели состояния экрана из [STATE_HOLDER_SUFFIXES].
 * SyncClip планирует ViewModel; канонический фильтр также распознаёт `Store`.
 */
class StateContractTest {

    private val productionScope = applicationScope()

    /**
     * Классы и интерфейсы, которые держат состояние экрана.
     *
     * Интерфейсы тоже могут быть контрактами держателей состояния.
     */
    private val stateHolders = productionScope.classesAndInterfaces()
        .withNameEndingWith(STATE_HOLDER_SUFFIXES)

    @Test
    fun `view model does not expose mutable state`() {
        Coverage.report("StateContractTest.mutableExposure", "держатели состояния", stateHolders.size)

        // Пустой список здесь — законный SKIPPED (Coverage это покажет), а не
        // падение: в проекте без фич держателей состояния просто нет.
        // Появится Store/ViewModel — проверка включится сама.
        stateHolders.assertTrue { viewModel ->
            viewModel.properties().none { property ->
                property.hasPublicOrDefaultModifier &&
                    property.text.contains("MutableStateFlow")
            }
        }
    }

    @Test
    fun `mutable state is not assigned directly`() {
        Coverage.report("StateContractTest.mutableAssignment", "держатели состояния", stateHolders.size)
        stateHolders.assertTrue { viewModel ->
            val mutableStateNames = viewModel.properties()
                .filter { it.text.contains("MutableStateFlow") }
                .map { it.name }
            mutableStateNames.none { name -> viewModel.text.contains("$name.value =") }
        }
    }

    @Test
    fun `state is not mutated directly`() {
        Coverage.report("StateContractTest.directMutation", "прикладные Kotlin-файлы", productionScope.files.size)
        productionScope.files.assertTrue { file ->
            !file.text.contains(Regex("""_state\.value\s*="""))
        }
    }

    @Test
    fun `state is not mutated through collection mutation`() {
        Coverage.report("StateContractTest.collectionMutation", "прикладные Kotlin-файлы", productionScope.files.size)
        productionScope.files.assertTrue { file ->
            !file.text.contains(Regex("""_state\.value\.[A-Za-z]+\.(add|remove|clear)\("""))
        }
    }

    @Test
    fun `compose state is not used inside view models`() {
        Coverage.report("StateContractTest.composeState", "держатели состояния", stateHolders.size)
        stateHolders.assertTrue { viewModel ->
            !viewModel.text.contains("mutableStateOf") &&
                !viewModel.text.contains("mutableStateListOf")
        }
    }

    @Test
    fun `android sources use lifecycle aware state collection`() {
        val androidFiles = productionScope.files.filter { it.sourceSetName in setOf("main", "androidMain") }
        Coverage.report("StateContractTest.lifecycleCollection", "файлы из main и androidMain", androidFiles.size)
        androidFiles.assertTrue { file ->
            !file.text.contains("collectAsState(")
        }
    }

    @Test
    fun `content does not receive view model`() {
        // INV-12: Content — stateless-слой. ViewModel в его параметрах означает,
        // что превью и переиспользование снова требуют DI.
        val contentFunctions = productionScope.functions().withNameEndingWith("Content")
        Coverage.report("StateContractTest.statelessContent", "функции *Content", contentFunctions.size)
        contentFunctions.assertTrue { function ->
            function.parameters.none { parameter ->
                parameter.type.name.contains("ViewModel")
            }
        }
    }
}
