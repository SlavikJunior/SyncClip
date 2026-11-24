pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "syncclip-backend"

include(
    ":server:contracts",
    ":server:core:config",
    ":server:core:security",
    ":server:core:database",
    ":server:core:auth",
    ":server:core:push",
    ":server:feature:auth",
    ":server:feature:devices",
    ":server:feature:sync",
    ":server:feature:notifications",
    ":server:app",
)
