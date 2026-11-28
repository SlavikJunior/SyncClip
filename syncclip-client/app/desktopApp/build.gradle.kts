import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.hot.reload)
}

group = "io.github.slavikjunior.syncclip"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "io.github.slavikjunior.syncclip.app.desktop.MainKt"

        nativeDistributions {
            // DataStore Preferences (protobuf-lite) использует sun.misc.Unsafe
            // из jdk.unsupported; без модуля запись настроек падает в runtime image.
            modules("jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SyncClip"
            packageVersion = "1.0.0"
        }
    }
}

dependencies {
    implementation(project(":app:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:platform"))
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jnativehook)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
