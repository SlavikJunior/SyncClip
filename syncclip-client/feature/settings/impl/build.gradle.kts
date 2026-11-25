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
            api(project(":core:database"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:di"))
            implementation(project(":feature:settings:api"))
            implementation(libs.koin.core)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
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
