package architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.nio.file.Path

/**
 * Каталоги, которые не являются исходниками клиента.
 *
 * `build-logic` — included build с инструментами сборки. `syncclip-backend` —
 * отдельная Gradle-сборка (Ktor/PostgreSQL): профиль `kmp-app` и эти проверки на
 * неё не распространяются (docs/PROFILE.md), у сервера свои проверки в README.
 */
private val EXCLUDED_SOURCE_DIRS = listOf("build-logic", "syncclip-backend")

/** Производственный код клиента: всё, кроме included build и серверной сборки. */
internal fun applicationScope(): KoScope {
    val root = Path.of(Konsist.projectRootPath).normalize()
    val excluded = EXCLUDED_SOURCE_DIRS.map { dir -> root.resolve(dir).normalize() }
    return Konsist.scopeFromProduction().slice { file ->
        val path = root.resolve(file.path).normalize()
        excluded.none { dir -> path.startsWith(dir) }
    }
}

internal fun List<KoFileDeclaration>.inLayer(pattern: String): List<KoFileDeclaration> {
    val segment = pattern.trim('.')
    return filter { file -> file.packagee?.name?.split('.')?.contains(segment) == true }
}
