import org.gradle.api.artifacts.ProjectDependency
import org.gradle.testing.jacoco.tasks.JacocoReport

apply(plugin = "jacoco")

// OPS-06: агрегированное покрытие всех модулей. JaCoCo-агент инструментирует
// классы зависимостей, поэтому тесты :server:app покрывают и core:*/contracts.
val jacocoAggregatedReport = tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Агрегированный JaCoCo-отчёт по всем серверным модулям."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "test" } })
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "classes" } })
    executionData.setFrom(files(subprojects.map { "${it.projectDir}/build/jacoco/test.exec" }))
    classDirectories.setFrom(
        subprojects.map { project ->
            fileTree("${project.projectDir}/build/classes/kotlin/main") { exclude("**/generated/**") }
        },
    )
    sourceDirectories.setFrom(subprojects.map { "${it.projectDir}/src/main/kotlin" })
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "NFR-10: покрытие сервера ≥ 75% строк."
    dependsOn(jacocoAggregatedReport)
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "classes" } })
    executionData.setFrom(files(subprojects.map { "${it.projectDir}/build/jacoco/test.exec" }))
    classDirectories.setFrom(
        subprojects.map { project ->
            fileTree("${project.projectDir}/build/classes/kotlin/main") { exclude("**/generated/**") }
        },
    )
    sourceDirectories.setFrom(subprojects.map { "${it.projectDir}/src/main/kotlin" })
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

// ============================================================================
// Проверка модульного графа (аналог INV-01…03 для сервера, R-1…R-3 дизайна):
//   feature:*  не зависит от feature:* и app
//   core:*     не зависит от feature:* и app
//   contracts  не зависит ни от одного проекта (только kotlinx.serialization)
// ============================================================================

val featurePrefix = ":server:feature:"
val corePrefix = ":server:core:"
val appPath = ":server:app"
val contractsPath = ":server:contracts"

val checkModuleDependencies by tasks.registering {
    group = "verification"
    description = "Проверяет направление зависимостей между серверными модулями (R-1…R-3)."

    doLast {
        val violations = mutableListOf<String>()
        val projectDependencies = allprojects.associate { project ->
            project.path to project.configurations
                .matching { it.name == "compileClasspath" || it.name == "runtimeClasspath" }
                .flatMap { configuration ->
                    configuration.dependencies.filterIsInstance<ProjectDependency>().map { it.path }
                }
                .toSet()
        }

        projectDependencies.forEach { (path, dependencies) ->
            when {
                path.startsWith(featurePrefix) -> dependencies
                    .filter { it.startsWith(featurePrefix) && it != path }
                    .forEach { violations += "$path -> $it (feature не может зависеть от feature, R-1)" }

                path.startsWith(corePrefix) -> dependencies
                    .filter { it.startsWith(featurePrefix) || it == appPath }
                    .forEach { violations += "$path -> $it (core не может зависеть от feature/app, R-2)" }

                path == contractsPath -> dependencies
                    .forEach { violations += "$path -> $it (contracts не зависит от проектов, R-3)" }
            }
            if (path.startsWith(featurePrefix) || path.startsWith(corePrefix)) {
                if (dependencies.contains(appPath)) {
                    violations += "$path -> $appPath (app — только корень композиции, R-5)"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Нарушения модульного графа (${violations.size}):")
                    violations.sorted().forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle("checkModuleDependencies: OK (${projectDependencies.size} проектов)")
    }
}

// Единая точка проверки: `./gradlew check` в корне прогоняет и граф, и тесты модулей.
// Ленивая конфигурация: пути задач разрешаются Gradle при выполнении,
// поэтому список модулей задан явно (промежуточные :server, :server:core — не проекты).
val checkableModules = listOf(
    ":server:contracts",
    ":server:core:config",
    ":server:core:security",
    ":server:core:database",
    ":server:core:auth",
    ":server:core:push",
    ":server:feature:auth",
    ":server:feature:devices",
    ":server:feature:sync",
    ":server:feature:notifications",
    ":server:app",
)

// ============================================================================
// Проверка слоёв по исходникам (аналог ArchUnit/Konsist без новой зависимости,
// ARCH-04): contracts не знает Ktor/Exposed/Koin, core не знает feature/app,
// feature domain не знает HTTP, в prod-коде нет GlobalScope/!!.
// ============================================================================
val checkSourceLayers by tasks.registering {
    group = "verification"
    description = "Проверяет импорты слоёв и запрещённые конструкции в исходниках (ARCH-04)."

    doLast {
        val violations = mutableListOf<String>()
        val serverDir = file("server")
        val ktorImport = Regex("(?m)^\\s*import\\s+io\\.ktor\\.")
        val exposedImport = Regex("(?m)^\\s*import\\s+org\\.jetbrains\\.exposed\\.")
        val koinImport = Regex("(?m)^\\s*import\\s+org\\.koin\\.")

        serverDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("/src/main/kotlin/") }
            .forEach { source ->
                val relative = source.relativeTo(rootDir).invariantSeparatorsPath
                val module = relative.substringBefore("/src/main/kotlin/")
                val text = source.readText()
                when {
                    module == "server/contracts" -> {
                        if (ktorImport.containsMatchIn(text)) violations += "$relative: contracts не импортирует Ktor (R-3)"
                        if (exposedImport.containsMatchIn(text)) violations += "$relative: contracts не импортирует Exposed (R-3)"
                        if (koinImport.containsMatchIn(text)) violations += "$relative: contracts не импортирует Koin (R-3)"
                    }

                    module == "server/core/config" || module == "server/core/security" -> {
                        if (ktorImport.containsMatchIn(text)) violations += "$relative: core не импортирует Ktor (R-4)"
                        if (exposedImport.containsMatchIn(text)) violations += "$relative: core не импортирует Exposed (R-4)"
                        if (koinImport.containsMatchIn(text)) violations += "$relative: core не импортирует Koin (R-4)"
                    }

                    module.startsWith("server/feature/") && relative.contains("/domain/") -> {
                        if (ktorImport.containsMatchIn(text)) violations += "$relative: feature/domain не импортирует Ktor (ARCH-04)"
                    }
                }
                if (Regex("\\bGlobalScope\\b").containsMatchIn(text)) {
                    violations += "$relative: GlobalScope запрещён (INV-21)"
                }
                if (text.contains("printStackTrace(")) {
                    violations += "$relative: printStackTrace запрещён (INV-21)"
                }
                if (Regex("!!").containsMatchIn(text)) {
                    violations += "$relative: !! запрещён в prod-коде (INV-21)"
                }
                // ARCH-01: репозиторные функции не публикуются напрямую —
                // только через фасад Repositories (internal + facade).
                if (module == "server/core/database" &&
                    relative.contains("/repository/") &&
                    !relative.endsWith("Repositories.kt") &&
                    Regex("(?m)^fun JdbcTransaction\\.").containsMatchIn(text)
                ) {
                    violations += "$relative: repository-функция должна быть internal (ARCH-01)"
                }
                if (module != "server/core/database" &&
                    Regex(
                        "(?m)^\\s*import\\s+io\\.github\\.slavikjunior\\.syncclip\\.server\\.core\\.database\\.repository\\.(?!Repositories\\.)",
                    ).containsMatchIn(text)
                ) {
                    violations += "$relative: фичи используют только facade Repositories.* (ARCH-01)"
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Нарушения слоёв (${violations.size}):")
                    violations.sorted().forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle("checkSourceLayers: OK")
    }
}

// Обновление lock-файлов: ./gradlew lockAllDependencies --write-locks.
// Резолвим конфигурации внутри каждого проекта: Gradle запрещает резолвить
// чужие конфигурации из корневой задачи без exclusive lock.
subprojects {
    tasks.register("lockDependencies") {
        group = "verification"
        description = "Резолвит основные конфигурации модуля для --write-locks."
        doLast {
            val names = listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
            names.forEach { name ->
                configurations.findByName(name)
                    ?.takeIf { it.isCanBeResolved }
                    ?.resolve()
            }
        }
    }
}

tasks.register("lockAllDependencies") {
    group = "verification"
    description = "Обновляет gradle.lockfile во всех модулях (использовать с --write-locks)."
    dependsOn(subprojects.map { it.tasks.named("lockDependencies") })
}

tasks.register("check") {
    group = "verification"
    description = "Архитектурная проверка графа + слоёв + lint/тесты всех модулей."
    dependsOn(checkModuleDependencies)
    dependsOn(checkSourceLayers)
    dependsOn(checkableModules.map { "$it:check" })
    dependsOn("jacocoCoverageVerification")
}
