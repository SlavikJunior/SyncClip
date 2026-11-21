package architecture

import androidx.compose.runtime.Composable
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.*
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * INV-13 — modifier первый опциональный параметр.
 * INV-14 — никаких хардкод-строк в UI.
 * INV-17 — цвета и типографика из MaterialTheme.
 */
class ComposeConventionTest {

    private val productionScope = applicationScope()
    private val composables = productionScope.functions()
        .withAnnotationOf(Composable::class)

    @Test
    fun `composable has modifier as first optional parameter`() {
        Coverage.report("ComposeConventionTest.modifier", "Composable-функции", composables.size)

        composables.assertTrue { function ->
            val parameters = function.parameters
            val modifierIndex = parameters.indexOfFirst { it.name == "modifier" }

            if (modifierIndex < 0) {
                // Функция без modifier — допустимо (например, stateless-обёртка).
                true
            } else {
                // Ни один обязательный параметр не должен стоять после modifier,
                // и все параметры до modifier — обязательные.
                parameters.take(modifierIndex).none { it.hasDefaultValue() }
            }
        }
    }

    @Test
    fun `composable functions use pascal case names`() {
        Coverage.report("ComposeConventionTest.pascalCase", "Composable-функции", composables.size)
        composables.assertTrue { function ->
            function.name.firstOrNull()?.isUpperCase() == true
        }
    }

    @Test
    fun `ui does not contain hardcoded text`() {
        Coverage.report("ComposeConventionTest.hardcodedText", "прикладные Kotlin-файлы (поиск Text)", productionScope.files.size)
        productionScope.files.assertTrue { file ->
            !HARDCODED_TEXT.containsMatchIn(file.text)
        }
    }

    @Test
    fun `ui does not contain hardcoded colors`() {
        val files = productionScope.files.filterNot { it.isPaletteFile() }
        Coverage.report("ComposeConventionTest.hardcodedColors", "прикладные Kotlin-файлы вне палитры (поиск Color)", files.size)
        files.assertTrue { file ->
            !HARDCODED_COLOR.containsMatchIn(file.text)
        }
    }

    private companion object {
        /**
         * Text("...") — строковый литерал как единственный аргумент.
         *
         * Кавычка внутри raw-строки невозможна без экранирования, поэтому
         * используется обычная строка: иначе regex молча теряет последний
         * символ и начинает матчить любое `Text(`.
         */
        val HARDCODED_TEXT = Regex("\\bText\\(\\s*\"")

        /** Color(0xFF......) — hex-литерал цвета. */
        val HARDCODED_COLOR = Regex("""\bColor\(\s*0x[0-9A-Fa-f]{8}""")
    }
}

/**
 * Файл, в котором законно объявлять цветовые литералы: палитра и тема.
 *
 * Канон исключает пакет `theme`. В этом проекте дизайн-система называется
 * `core/designsystem` и лежит в корне пакета модуля, поэтому правило
 * признаёт и такой расклад — иначе проверка ругается на саму палитру.
 */
private fun KoFileDeclaration.isPaletteFile(): Boolean {
    if (path.replace('\\', '/').contains("/theme/")) return true
    return moduleName.contains("designsystem") && (name == "Color" || name == "Theme")
}
