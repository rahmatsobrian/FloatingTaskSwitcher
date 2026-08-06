pluginManagement {
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
        // Shizuku (dev.rikka.shizuku:api/provider) is published on JitPack, not Maven Central.
        maven("https://jitpack.io")
    }
}

rootProject.name = "ResourceTransfer"
include(":app")
