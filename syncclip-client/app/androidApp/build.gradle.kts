plugins {
    id("convention.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.google.services)
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
    implementation(project(":feature:sync:api"))
    implementation(project(":core:firebase"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)

    // BOM выравнивает firebase-auth и firebase-messaging; версии пакетов — в нём.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    debugImplementation(compose.uiTooling)
}
