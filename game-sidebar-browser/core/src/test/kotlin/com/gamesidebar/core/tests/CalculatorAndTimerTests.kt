package com.gamesidebar.core.tests

import com.gamesidebar.core.calc.Calculator
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.timer.TimerState

object CalculatorAndTimerTests {

    private fun value(expression: String): Double = when (val r = Calculator.evaluate(expression)) {
        is Calculator.Result.Value -> r.value
        else -> error("expected a value for '$expression' but got $r")
    }

    private fun display(expression: String): String = when (val r = Calculator.evaluate(expression)) {
        is Calculator.Result.Value -> r.display
        else -> error("expected a value for '$expression' but got $r")
    }

    val cases: List<TestCase> = listOf(
        TestCase("addition and subtraction") {
            Assert.close(7.0, value("2 + 5"))
            Assert.close(-3.0, value("2 - 5"))
        },
        TestCase("multiplication and division with precedence") {
            Assert.close(14.0, value("2 + 3 × 4"))
            Assert.close(3.5, value("7 ÷ 2"))
            Assert.close(11.0, value("2 × 3 + 5"))
        },
        TestCase("parentheses override precedence") {
            Assert.close(20.0, value("(2 + 3) × 4"))
            Assert.close(2.0, value("((1 + 1))"))
        },
        TestCase("unary minus at the start, after an operator and after a paren") {
            Assert.close(-4.0, value("-4"))
            Assert.close(-1.0, value("3 + -4"))
            Assert.close(-6.0, value("-(2 + 4)"))
            Assert.close(2.0, value("-(-2)"))
        },
        TestCase("unary plus is ignored") {
            Assert.close(5.0, value("+5"))
            Assert.close(9.0, value("4 + +5"))
        },
        TestCase("postfix percent divides by 100") {
            Assert.close(0.5, value("50%"))
            Assert.close(0.25, value("50% ÷ 2"))
            Assert.close(0.0, value("3 - 50% × 6"))
            Assert.close(-3.0, value("3 - 50% × 12"))
        },
        TestCase("division by zero is an error, not infinity") {
            val r = Calculator.evaluate("1 ÷ 0")
            Assert.that(r is Calculator.Result.Error) { "expected error, got $r" }
            Assert.equals("calc_error_div_zero", (r as Calculator.Result.Error).reasonKey)
        },
        TestCase("unbalanced parentheses are an error") {
            Assert.that(Calculator.evaluate("(1 + 2") is Calculator.Result.Error)
            Assert.that(Calculator.evaluate("1 + 2)") is Calculator.Result.Error)
        },
        TestCase("trailing operator is incomplete, not an error") {
            Assert.equals(Calculator.Result.Incomplete, Calculator.evaluate("12 +"))
            Assert.equals(Calculator.Result.Incomplete, Calculator.evaluate("12 × ("))
            Assert.equals(Calculator.Result.Incomplete, Calculator.evaluate(""))
        },
        TestCase("garbage input is a syntax error") {
            Assert.that(Calculator.evaluate("12 & 3") is Calculator.Result.Error)
            Assert.that(Calculator.evaluate("1..2 + 1") is Calculator.Result.Error)
        },
        TestCase("display glyphs are translated for the engine") {
            Assert.equals("12 * 3 / 2", Calculator.toEngineExpression("12 × 3 ÷ 2"))
            Assert.equals("12 × 3 ÷ 2", Calculator.toDisplayExpression("12 * 3 / 2"))
        },
        TestCase("results are formatted without trailing zeros") {
            Assert.equals("7", display("3 + 4"))
            Assert.equals("0.5", display("1 ÷ 2"))
            Assert.equals("0", display("0"))
            Assert.equals("0.3", display("0.1 + 0.2"))
            Assert.equals("-2.5", display("-5 ÷ 2"))
        },
        TestCase("long results keep ten decimals at most") {
            val text = display("1 ÷ 3")
            Assert.equals("0.3333333333", text)
        },
        TestCase("huge and tiny results use scientific notation") {
            Assert.that(display("999999999999 × 1000").contains("e")) { "expected scientific notation" }
            Assert.that(display("0.0000000001").contains("e")) { "expected scientific notation" }
        },
        TestCase("stopwatch accumulates across pause and resume") {
            var t = TimerState(mode = TimerState.Mode.STOPWATCH)
            t = t.start(1_000)
            t = t.tick(4_000)
            Assert.equals(3_000L, t.elapsed(4_000))
            t = t.pause(4_000)
            Assert.equals(3_000L, t.elapsed(9_000), "paused timer must not advance")
            t = t.start(9_000)
            Assert.equals(5_000L, t.elapsed(11_000))
        },
        TestCase("reset clears everything") {
            var t = TimerState().start(0).tick(5_000).reset()
            Assert.equals(0L, t.elapsed(9_000))
            Assert.that(!t.running) { "timer should be stopped after reset" }
        },
        TestCase("countdown counts down and finishes once") {
            var t = TimerState(mode = TimerState.Mode.COUNTDOWN, targetMs = 10_000)
            t = t.start(0)
            Assert.equals(6_000L, t.remaining(4_000))
            t = t.tick(12_000)
            Assert.that(t.finished) { "countdown should be finished" }
            Assert.that(!t.running) { "finished countdown must stop" }
            Assert.equals(0L, t.remaining(13_000))
            val again = t.tick(14_000)
            Assert.that(!again.running) { "finished timer stays stopped" }
        },
        TestCase("countdown cannot go negative while paused") {
            val t = TimerState(mode = TimerState.Mode.COUNTDOWN, targetMs = 2_000).start(0).pause(1_000)
            Assert.equals(1_000L, t.remaining(60_000))
        },
        TestCase("changing the target resets the run") {
            val t = TimerState().start(0).tick(5_000).withTarget(60_000)
            Assert.equals(0L, t.accumulatedMs)
            Assert.that(!t.running) { "changing target should stop the timer" }
            Assert.equals(TimerState.Mode.COUNTDOWN, t.mode)
        },
        TestCase("toggle starts and pauses") {
            var t = TimerState()
            t = t.toggle(0)
            Assert.that(t.running) { "toggle should start" }
            t = t.toggle(1_000)
            Assert.that(!t.running) { "toggle should pause" }
            Assert.equals(1_000L, t.accumulatedMs)
        },
        TestCase("clock labels are fixed width") {
            Assert.equals("00:05", TimerState.formatClock(5_000, withCentiseconds = false))
            Assert.equals("1:02:03", TimerState.formatClock(3_723_000, withCentiseconds = false))
            Assert.equals("00:05.00", TimerState.formatClock(5_000, withCentiseconds = true))
            Assert.equals("00:00", TimerState.formatClock(-5, withCentiseconds = false))
        },
        TestCase("countdown presets are one to thirty minutes") {
            Assert.equals(listOf(1, 3, 5, 10, 15, 30), TimerState.COUNTDOWN_PRESETS_MS.map { (it / 60_000).toInt() })
        },
    )
}
