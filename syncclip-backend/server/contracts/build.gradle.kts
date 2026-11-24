plugins {
    id("syncclip.jvm-library")
}

dependencies {
    // R-3: contracts зависит только от kotlinx.serialization (+ JDK).
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
