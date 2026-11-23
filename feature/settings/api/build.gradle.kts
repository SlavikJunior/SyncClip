plugins {
    id("convention.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:model"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
