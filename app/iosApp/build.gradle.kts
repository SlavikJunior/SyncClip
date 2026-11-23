plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "io.github.slavikjunior.syncclip"
version = "0.1.0"

kotlin {
    jvmToolchain(21)

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:common"))
            implementation(libs.koin.core)
            implementation(compose.ui)
        }
    }
}
