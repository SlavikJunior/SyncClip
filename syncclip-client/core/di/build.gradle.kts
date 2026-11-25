plugins {
    id("convention.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:database"))
            implementation(libs.androidx.room.runtime)
            implementation(libs.koin.core)
        }
    }
}
