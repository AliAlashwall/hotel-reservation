pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HotelReservation"

include(":app")

// Layers, innermost first. Nothing here may depend on anything below it.
include(":core-model")      // pure Kotlin: domain types, no Android, no framework
include(":core-domain")     // pure Kotlin: repository contracts and use cases
include(":core-data")       // repository implementations, cache and refresh policy
include(":core-network")    // LiteAPI client, DTOs, DTO to domain mapping
include(":core-database")   // Room schema, DAOs, cache metadata
include(":core-ui")         // theme, design system, shared state composables
include(":core-testing")    // fakes and test infrastructure, consumed by tests only

include(":navigation")      // type-safe routes, shared so features never depend on each other

include(":feature-hotels")
include(":feature-detail")
include(":feature-favorites")
include(":feature-booking")
