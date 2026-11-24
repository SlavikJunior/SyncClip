plugins {
    id("syncclip.jvm-library")
}

dependencies {
    api(project(":server:contracts"))
    api(project(":server:core:config"))
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)
    api(libs.postgresql)
    api(libs.hikaricp)
    api(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
