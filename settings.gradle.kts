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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EasyBill"

include(":app")
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:datastore")
include(":core:database")
include(":core:data")
include(":core:print")
include(":feature:dashboard")
include(":feature:settings")
include(":feature:onboarding")
include(":feature:items")
include(":feature:parties")
include(":feature:billing")
include(":feature:money")
include(":feature:reports")
