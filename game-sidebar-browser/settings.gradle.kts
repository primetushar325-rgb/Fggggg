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

rootProject.name = "GameSidebarBrowser"

// :core holds every piece of logic that does not need a device: URL routing, search engine
// selection, the calculator parser, the timer state machine, panel/handle geometry, edge snapping,
// tab/shortcut/bookmark/history/note list logic, download parsing and the privacy policy. It is
// pure Kotlin/JVM with zero dependencies, so `./gradlew :core:test` (or the offline runner
// tools/run_core_tests.sh) verifies it on any machine.
//
// :app is the Android shell: Compose screens, the overlay foreground service, WindowManager
// handle/panel, WebView management, Room and DataStore. Keeping the two apart is what makes the
// behaviour testable and stops UI code from owning business rules.
include(":core")
include(":app")
