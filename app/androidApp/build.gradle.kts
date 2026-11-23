plugins {
    id("convention.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.slavikjunior.syncclip.app.android"

    defaultConfig {
        applicationId = "io.github.slavikjunior.syncclip"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":app:common"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)

    debugImplementation(compose.uiTooling)
}
