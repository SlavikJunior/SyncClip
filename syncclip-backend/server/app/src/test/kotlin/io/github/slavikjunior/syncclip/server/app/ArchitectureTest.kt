package io.github.slavikjunior.syncclip.server.app

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * ARCH-04: правила слоёв на уровне байткода (ArchUnit).
 *
 * Дополняет `checkModuleDependencies` (Gradle-граф) и `checkSourceLayers`:
 * здесь проверяются реальные зависимости классов, включая транзитивные импорты,
 * которые не видны по конфигурациям Gradle.
 */
@AnalyzeClasses(
    packages = ["io.github.slavikjunior.syncclip.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {

    @ArchTest
    val contractsAreIndependent: ArchRule = noClasses()
        .that().resideInAPackage("..server.contracts..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "io.ktor..",
            "org.jetbrains.exposed..",
            "org.koin..",
            "..server.core..",
            "..server.feature..",
            "..server.app..",
        )
        .because("contracts — чистые DTO/порты (R-3)")

    @ArchTest
    val coreConfigAndSecurityDoNotKnowInfrastructure: ArchRule = noClasses()
        .that().resideInAnyPackage("..server.core.config..", "..server.core.security..")
        .should().dependOnClassesThat().resideInAnyPackage("io.ktor..", "org.jetbrains.exposed..", "org.koin..")
        .because("config/security не должны зависеть от HTTP/БД/DI (R-4)")

    @ArchTest
    val featureDomainIsHttpFree: ArchRule = noClasses()
        .that().resideInAPackage("..server.feature..domain..")
        .should().dependOnClassesThat().resideInAPackage("io.ktor..")
        .because("HTTP-слой живёт только в feature/*/api")

    @ArchTest
    val noGlobalScope: ArchRule = noClasses()
        .should().dependOnClassesThat().haveFullyQualifiedName("kotlinx.coroutines.GlobalScope")
        .because("INV-21: GlobalScope запрещён")
}
