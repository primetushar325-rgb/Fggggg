package com.gamesidebar.core.tests

import com.gamesidebar.core.harness.SuiteResult
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.harness.runSuite
import kotlin.system.exitProcess

/**
 * Registry of every core test suite.
 *
 * [main] runs them directly (no JUnit on the classpath needed, which is how CI and sandboxed
 * machines verify the engine), and `core/src/test/.../CoreLibraryTest.kt` runs the exact same
 * registry under `./gradlew :core:test`. One copy of the test logic, two ways to execute it.
 */
object CoreTestSuites {

    val suites: List<Pair<String, List<TestCase>>> = listOf(
        "Address bar routing" to UrlResolverTests.cases,
        "WebView safety policy" to SafetyTests.cases,
        "Tabs & quick shortcuts" to TabAndShortcutTests.cases,
        "Shortcut persistence codec" to ShortcutCodecTests.cases,
        "Calculator & timer" to CalculatorAndTimerTests.cases,
        "Overlay geometry & edge snap" to GeometryTests.cases,
        "Landscape, chrome & panel state geometry" to PanelStateGeometryTests.cases,
        "Downloads" to DownloadTests.cases,
        "Bookmarks, history, notes, clipboard" to DataStoreTests.cases,
        "Settings, commands & formatting" to SettingsAndCommandTests.cases,
    )

    val totalTests: Int get() = suites.sumOf { it.second.size }

    fun runAll(verbose: Boolean = true): List<SuiteResult> = suites.map { (name, cases) ->
        val result = runSuite(name, cases)
        if (verbose) print(result)
        result
    }

    private fun print(result: SuiteResult) {
        val status = if (result.isSuccess) "PASS" else "FAIL"
        println("[$status] ${result.suiteName}: ${result.passed}/${result.total} (${result.durationMillis} ms)")
        for (failure in result.failures) {
            println("        x ${failure.testName}")
            println("          ${failure.message}")
        }
    }
}

fun main() {
    println("Game SideBar core engine - ${CoreTestSuites.totalTests} tests")
    println("----------------------------------------------------------------")
    val started = System.nanoTime()
    val results = CoreTestSuites.runAll()
    val millis = (System.nanoTime() - started) / 1_000_000

    val passed = results.sumOf { it.passed }
    val failed = results.sumOf { it.failures.size }
    println("----------------------------------------------------------------")
    println("$passed passed, $failed failed in $millis ms")
    if (failed > 0) exitProcess(1)
}
