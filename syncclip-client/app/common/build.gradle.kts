plugins {
    id("convention.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Compose UI test на Desktop требует skiko-нативы; модуль берёт их тем же
    // путём, что и feature-модули со скриншот-тестами.
    id("io.github.takahirom.roborazzi")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:designsystem"))
            implementation(project(":core:di"))
            implementation(project(":core:datastore"))
            implementation(project(":core:firebase"))
            implementation(project(":shared:model"))
            implementation(project(":feature:auth:api"))
            implementation(project(":feature:auth:impl"))
            implementation(project(":feature:history:api"))
            implementation(project(":feature:history:impl"))
            implementation(project(":feature:settings:api"))
            implementation(project(":feature:settings:impl"))
            implementation(project(":feature:sync:api"))
            implementation(project(":feature:sync:impl"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            // NavDisplay и back stack: форк JetBrains нужен ради desktop/iOS-таргетов.
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.jetbrains.androidx.navigation3.ui)
            // rememberListDetailSceneStrategy: сцены Nav3 для двухпанельного режима.
            implementation(libs.cmp.material3.adaptive.navigation3)
        }
        commonTest.dependencies {
            implementation(libs.cmp.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            // Compose UI test: проверка адаптивных сцен Nav3 (list-detail).
            implementation(libs.cmp.ui.test)
            implementation(libs.roborazzi.compose.desktop)
            implementation(libs.junit)
            // Тест работы с Room использует DAO напрямую, а не через реэкспорт
            // из feature-модуля (INV-26).
            implementation(project(":core:database"))
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/jvmTest/screenshots"))
}
