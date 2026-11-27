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
            // Swift-мост iOS реализует Kotlin-контракт Firebase, поэтому типы
            // :core:firebase должны попасть в заголовки фреймворка.
            export(project(":core:firebase"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:common"))
            api(project(":core:firebase"))
            implementation(libs.koin.core)
            implementation(compose.ui)
        }
    }
}
