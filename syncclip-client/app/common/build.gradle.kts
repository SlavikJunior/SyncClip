plugins {
    id("convention.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
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
        }
        commonTest.dependencies {
            implementation(libs.cmp.runtime)
        }
        jvmTest.dependencies {
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
