plugins {
    id("convention.kmp.compose")
    id("io.github.takahirom.roborazzi")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:model"))
            implementation(project(":feature:sync:api"))
            implementation(project(":core:cache"))
            implementation(project(":core:designsystem"))
            implementation(libs.koin.core)
            implementation(libs.cmp.lifecycle.viewmodel)
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
