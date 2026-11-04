pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChibaCan"
include(":app")
include(":baselineprofile")

// The project is named zzu-kotlin but publishes dev.zzu:zzu, so Gradle cannot match them by itself.
includeBuild("../../IDEA/zzu-kotlin") {
    dependencySubstitution {
        substitute(module("dev.zzu:zzu")).using(project(":"))
    }
}
