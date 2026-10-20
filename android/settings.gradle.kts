// Application Android de FasoGuardian : une Trusted Web Activity qui ouvre l'application Parents en plein écran
// (ADR 0040). Tout le code de l'application reste celui de la PWA ; ce projet n'en est que l'enveloppe.
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
    }
}

rootProject.name = "fasoguardian-android"
include(":app")
