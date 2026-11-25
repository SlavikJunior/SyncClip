pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

includeBuild("build-logic")
include(":core:architecture-tests")

include(":shared:model")
include(":shared:network")
include(":core:cache")
include(":core:database")
include(":core:designsystem")
include(":core:di")

include(":feature:history:api")
include(":feature:history:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
include(":feature:sync:api")
include(":feature:sync:impl")

include(":app:common")
include(":app:androidApp")
include(":app:iosApp")
include(":app:desktopApp")

rootProject.name = "SyncClip"
