plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The engine is pure Kotlin/JVM: no Android imports, no reflection, no third-party libraries.
// That is what allows `./gradlew :core:test` - and the SDK-free runner tools/run_core_tests.sh - to
// verify URL routing, the calculator, the timer, panel geometry, edge snapping, tab/bookmark/
// history/note logic, download parsing and the privacy policy on any machine.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
