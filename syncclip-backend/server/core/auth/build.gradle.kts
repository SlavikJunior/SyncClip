plugins {
    id("syncclip.jvm-library")
}

dependencies {
    api(project(":server:contracts"))
    api(project(":server:core:database"))
}

// Имена jar'ов должны быть уникальны: в distribution оба модуля `auth`
// (core:auth и feature:auth) иначе дают duplicate entry lib/auth.jar.
tasks.withType<Jar>().configureEach {
    archiveBaseName.set("syncclip-core-auth")
}
