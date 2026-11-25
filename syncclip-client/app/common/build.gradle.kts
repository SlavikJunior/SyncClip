plugins {
    id("convention.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:designsystem"))
            implementation(project(":core:di"))
            implementation(project(":shared:model"))
            implementation(project(":feature:history:api"))
            implementation(project(":feature:history:impl"))
            implementation(project(":feature:settings:api"))
            implementation(project(":feature:settings:impl"))
            implementation(project(":feature:sync:api"))
            implementation(project(":feature:sync:impl"))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.cmp.runtime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
