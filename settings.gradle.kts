pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
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

rootProject.name = "ghealth-tools"

include(":app")

include(":core:core-ui")
include(":core:core-common")
include(":core:core-model")
include(":core:core-data")
include(":core:core-database")
include(":core:core-network")
include(":core:core-datastore")
include(":core:core-storage")

include(":feature:feature-login")
include(":feature:feature-connection")
include(":feature:feature-demo")
include(":feature:feature-factory")
include(":feature:feature-settings")

include(":ble:ble-scanner")
include(":ble:ble-connection")
include(":ble:ble-protocol")

include(":ble:ble-itlvc")
include(":ble:ble-gh3220")

include(":external:libcom")
include(":external:libdfu2")
include(":feature:feature-ota")
