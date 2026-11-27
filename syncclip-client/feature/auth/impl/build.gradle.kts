plugins {
    id("convention.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":feature:auth:api"))
            implementation(project(":core:datastore"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:firebase"))
            implementation(project(":shared:network"))
            implementation(libs.koin.core)
            // koinViewModel(): ViewModel живёт в ViewModelStore, а не в remember (INV-08).
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
