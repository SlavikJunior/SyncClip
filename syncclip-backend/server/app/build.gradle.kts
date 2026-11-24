plugins {
    id("syncclip.jvm-app")
}

application {
    mainClass.set("io.github.slavikjunior.syncclip.server.app.MainKt")
}

dependencies {
    implementation(project(":server:contracts"))
    implementation(project(":server:core:config"))
    implementation(project(":server:core:security"))
    implementation(project(":server:core:database"))
    implementation(project(":server:core:auth"))
    implementation(project(":server:core:push"))
    implementation(project(":server:feature:auth"))
    implementation(project(":server:feature:devices"))
    implementation(project(":server:feature:sync"))
    implementation(project(":server:feature:notifications"))

    implementation(libs.bundles.ktor.server)
    implementation(libs.koin.ktor)
    implementation(libs.koin.core)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.ktor.test)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.snakeyaml)
}
