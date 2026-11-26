// Lets the pure-Kotlin core be built and tested on its own (no Android SDK needed):
//   gradle -p core test
pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
    plugins {
        kotlin("jvm") version "2.2.21"
        kotlin("plugin.serialization") version "2.2.21"
    }
}
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "core"
