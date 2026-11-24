plugins {
    id("syncclip.jvm-library")
}

dependencies {
    api(project(":server:core:config"))
    api(libs.java.jwt)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
