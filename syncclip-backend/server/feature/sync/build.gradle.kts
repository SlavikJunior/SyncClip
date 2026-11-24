plugins {
    id("syncclip.jvm-library")
}

dependencies {
    api(project(":server:contracts"))
    api(project(":server:core:config"))
    api(project(":server:core:database"))
    api(project(":server:core:auth"))
    api(project(":server:core:security"))
    api(project(":server:core:push"))
    api(libs.ktor.server.core)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.websockets)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
