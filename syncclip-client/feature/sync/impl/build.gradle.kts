plugins {
    id("convention.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:crypto"))
            implementation(project(":shared:model"))
            implementation(project(":shared:network"))
            implementation(project(":feature:sync:api"))
            implementation(project(":core:cache"))
            implementation(project(":core:clipboard"))
            implementation(project(":core:database"))
            implementation(project(":core:datastore"))
            implementation(project(":core:lan"))
            implementation(project(":core:platform"))
            implementation(project(":core:designsystem"))
            implementation(libs.koin.core)
            // koinViewModel(): ViewModel живёт в ViewModelStore, а не в remember (INV-08).
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.cmp.ui.test)
            implementation(libs.roborazzi.compose.desktop)
            implementation(libs.junit)
            // Локальный relay для e2e двух клиентов через настоящий WebSocket.
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/jvmTest/screenshots"))
}
