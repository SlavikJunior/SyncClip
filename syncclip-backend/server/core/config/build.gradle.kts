plugins {
    id("syncclip.jvm-library")
}

dependencies {
    api(project(":server:contracts"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
