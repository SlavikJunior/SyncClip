plugins {
    id("convention.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:model"))
            // api здесь выносил DAO в ABI фичи, хотя публичная поверхность —
            // только NavEntry и Koin-модуль (INV-26).
            implementation(project(":core:database"))
            implementation(project(":core:datastore"))
            implementation(project(":core:cache"))
            implementation(project(":core:clipboard"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:di"))
            implementation(project(":feature:history:api"))
            implementation(libs.koin.core)
            // koinViewModel(): ViewModel живёт в ViewModelStore, а не в remember (INV-08).
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
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
