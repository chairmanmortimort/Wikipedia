pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Light's LP3 keyboard library is published to GitHub Packages and is a
        // transitive dependency of :sdk:ui. Credentials come from the gitignored
        // local.properties (gpr.user / gpr.key) or the GH_PACKAGES_* environment
        // variables — never committed to the repository.
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
        // Public mirror fallback (no credentials required).
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "light-sdk"

includeBuild("plugin")
include(":lint-rules")
include(":sdk:shared")
include(":sdk:ui")
include(":sdk:client")
include(":sdk:server")
include(":sdk:emulator")
include(":tool")
