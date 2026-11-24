import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Базовый convention-плагин серверной библиотеки: JVM-only, JDK 21,
// единые opt-in'ы Kotlin и настройка тестов. Версии зависимостей —
// только из каталога модуля (INV-23), поэтому здесь координат нет.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("dev.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    `java-library`
    jacoco
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-Xjsr305=strict",
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// OPS-06: покрытие измеряется всегда (XML для CI + HTML для разработчика).
tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// OPS-06: статический анализ с baseline (существующие находки зафиксированы,
// новые — валят сборку). Baseline-файлы генерируются detektBaseline /
// ktlintGenerateBaseline и лежат в модуле.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    baseline = file("detekt-baseline.xml")
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = "21"
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.8.0")
    baseline.set(file("ktlint-baseline.xml"))
}

tasks.named("check") {
    dependsOn("detekt", "ktlintCheck")
}

// OPS-06: dependency locking даёт воспроизводимую сборку и вход для OSV-сканера
// (`gradle.lockfile`). Обновление: ./gradlew dependencies --write-locks.
dependencyLocking {
    lockAllConfigurations()
}
