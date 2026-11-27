plugins {
    id("convention.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:model"))
            implementation(project(":shared:network"))
            implementation(project(":core:cache"))
            // См. history/impl: DAO в ABI фичи не нужен, datastore импортируется
            // кодом напрямую — объявляем явно (INV-26).
            implementation(project(":core:database"))
            implementation(project(":core:datastore"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:di"))
            implementation(project(":feature:settings:api"))
            implementation(libs.koin.core)
            // koinViewModel(): ViewModel живёт в ViewModelStore, а не в remember (INV-08).
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.test)
        }
        jvmTest.dependencies {
            implementation(libs.cmp.ui.test)
            implementation(libs.roborazzi.compose.desktop)
            implementation(libs.junit)
        }
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/jvmTest/screenshots"))
}
