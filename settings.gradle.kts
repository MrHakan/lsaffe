pluginManagement {
    includeBuild("build-logic")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "deckwatch"

include(":app")
include(":core:core-model")
include(":core:core-common")
include(":core:core-database")
include(":core:core-datastore")
include(":core:core-designsystem")
include(":core:core-testing")
include(":feature:feature-notes")
include(":feature:feature-vessel")
include(":feature:feature-deckview")
include(":feature:feature-equipment")
include(":feature:feature-inspection")
include(":feature:feature-survivalcraft")
include(":feature:feature-report")
include(":feature:feature-settings")
include(":data:data-repository")
include(":data:data-seed")
