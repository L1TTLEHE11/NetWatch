pluginManagement {
    repositories {
        google()
        providers.gradleProperty("mavenMirror").orNull?.let { maven(it) }   // optional local mirror (~/.gradle/gradle.properties)
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        providers.gradleProperty("mavenMirror").orNull?.let { maven(it) }   // optional local mirror (~/.gradle/gradle.properties)
        mavenCentral()
    }
}
rootProject.name = "NetWatch"
include(":app")
