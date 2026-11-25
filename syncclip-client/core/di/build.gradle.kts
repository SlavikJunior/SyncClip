plugins {
    id("convention.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:clipboard"))
            api(project(":core:database"))
            api(project(":core:datastore"))
            api(project(":core:firebase"))
            api(project(":core:lan"))
            implementation(project(":shared:network"))
            implementation(libs.androidx.room.runtime)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
