package com.gamesidebar.core.timer

/**
 * Stopwatch + countdown state machine.
 *
 * Pure and clock-driven: the Android layer feeds it `SystemClock.elapsedRealtime()` from a single
 * `Choreographer`/`Handler` tick, so there is exactly one timer source and nothing polls when the
 * timer is stopped (a gaming-mode requirement: no background CPU).
 */
data class TimerState(
    val mode: Mode = Mode.STOPWATCH,
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val targetMs: Long = DEFAULT_COUNTDOWN_MS,
    val startedAtMs: Long = 0L,
    val accumulatedMs: Long = 0L,
    val finished: Boolean = false,
) {
    enum class Mode { STOPWATCH, COUNTDOWN }

    /** Elapsed time, derived from the clock while running so pause/resume cannot drift. */
    fun elapsed(nowMs: Long): Long {
        val live = if (running) accumulatedMs + (nowMs - startedAtMs) else accumulatedMs
        return if (mode == Mode.COUNTDOWN) live.coerceAtMost(targetMs) else live
    }

    fun remaining(nowMs: Long): Long =
        if (mode == Mode.COUNTDOWN) (targetMs - elapsed(nowMs)).coerceAtLeast(0L) else 0L

    fun isFinished(nowMs: Long): Boolean = mode == Mode.COUNTDOWN && remaining(nowMs) <= 0L

    fun primaryLabel(nowMs: Long): String = when (mode) {
        Mode.STOPWATCH -> formatClock(elapsed(nowMs), withCentiseconds = true)
        Mode.COUNTDOWN -> formatClock(remaining(nowMs), withCentiseconds = remaining(nowMs) < 60_000)
    }

    fun start(nowMs: Long): TimerState = when {
        running -> this
        finished -> this
        else -> copy(running = true, startedAtMs = nowMs)
    }

    fun pause(nowMs: Long): TimerState =
        if (!running) this else copy(running = false, accumulatedMs = elapsed(nowMs))

    fun toggle(nowMs: Long): TimerState = if (running) pause(nowMs) else start(nowMs)

    fun reset(): TimerState = copy(
        running = false,
        elapsedMs = 0L,
        accumulatedMs = 0L,
        startedAtMs = 0L,
        finished = false,
    )

    fun withMode(mode: Mode): TimerState = reset().copy(mode = mode)

    fun withTarget(targetMs: Long): TimerState =
        reset().copy(mode = Mode.COUNTDOWN, targetMs = targetMs.coerceAtLeast(1_000L))

    /** Called by the UI tick; flips `finished` exactly once when a countdown hits zero. */
    fun tick(nowMs: Long): TimerState {
        if (!running) return this
        val done = isFinished(nowMs)
        return if (done && !finished) {
            copy(finished = true, running = false, accumulatedMs = targetMs)
        } else {
            copy(elapsedMs = elapsed(nowMs))
        }
    }

    companion object {
        const val DEFAULT_COUNTDOWN_MS = 10 * 60 * 1000L
        val COUNTDOWN_PRESETS_MS = listOf(1, 3, 5, 10, 15, 30).map { it * 60_000L }

        /** "01:23.4" / "1:02:03" - fixed-width so the overlay panel does not reflow. */
        fun formatClock(totalMs: Long, withCentiseconds: Boolean): String {
            val safe = totalMs.coerceAtLeast(0L)
            val hours = safe / 3_600_000L
            val minutes = (safe % 3_600_000L) / 60_000L
            val seconds = (safe % 60_000L) / 1_000L
            val centis = (safe % 1_000L) / 10L
            val base = if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
            return if (withCentiseconds) String.format("%s.%02d", base, centis) else base
        }
    }
}
