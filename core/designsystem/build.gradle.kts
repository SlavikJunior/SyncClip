plugins {
    id("convention.kmp.compose")
    id("io.github.takahirom.roborazzi")
}

kotlin {
    sourceSets {
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
