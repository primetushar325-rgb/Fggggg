package com.rigstudio.core.tests

import com.rigstudio.core.export.ExportFrameRate
import com.rigstudio.core.export.ExportResolution
import com.rigstudio.core.model.AppSettings
import com.rigstudio.core.model.AppSettingsCodec
import com.rigstudio.core.model.asExportSeed
import com.rigstudio.core.util.HistoryStack
import com.rigstudio.core.harness.Assert
import com.rigstudio.core.harness.TestCase

/**
 * V4 editor infrastructure: the undo/redo stack behind the editor top bar (§28) and the
 * app-settings document behind the Settings screen and hidden debug mode (§49, §52).
 */
object EditorInfraTests {

    val cases: List<TestCase> = listOf(

        TestCase("history stack undoes and redoes symmetrically") {
            val history = HistoryStack<String>()
            Assert.that(!history.canUndo) { "fresh stack should not be undoable" }
            Assert.that(!history.canRedo) { "fresh stack should not be redoable" }

            history.record("state-a")          // about to change a -> b
            Assert.that(history.canUndo)
            Assert.equals("state-a", history.undo("state-b"))
            Assert.that(!history.canUndo) { "undo consumed the only step" }
            Assert.that(history.canRedo)
            Assert.equals("state-b", history.redo("state-a"))
            Assert.that(history.canUndo) { "redo files the state back onto undo" }
            Assert.that(!history.canRedo)
        },

        TestCase("recording after undo clears the redo branch") {
            val history = HistoryStack<String>()
            history.record("a")
            history.undo("b")
            Assert.that(history.canRedo)
            history.record("c")                 // a new edit invalidates redo
            Assert.that(!history.canRedo) { "redo must be cleared by a new record" }
            Assert.equals("a", history.undo("d"))
        },

        TestCase("history stack is bounded and drops the oldest snapshot") {
            val history = HistoryStack<Int>(capacity = 2)
            history.record(1)
            history.record(2)
            history.record(3)
            Assert.equals(2, history.size) { "capacity 2 must hold at most 2 snapshots" }
            Assert.equals(2, history.undo(4))  { "oldest (1) should have fallen off" }
            Assert.equals(3, history.undo(5))
            Assert.that(!history.canUndo)
        },

        TestCase("undo and redo with an empty stack are safe no-ops") {
            val history = HistoryStack<Int>()
            Assert.equals(null, history.undo(9))
            Assert.equals(null, history.redo(9))
        },

        TestCase("app settings round-trip through JSON") {
            val settings = AppSettings(
                defaultResolution = ExportResolution.HD_720,
                defaultFrameRate = ExportFrameRate.FPS_60,
                loopByDefault = false,
                debugOverlays = true,
            )
            val restored = AppSettingsCodec.decodeJsonOrNull(AppSettingsCodec.encodeJson(settings))
            Assert.that(restored != null) { "settings failed to decode" }
            Assert.equals(settings, restored)
        },

        TestCase("app settings defaults match the V4 spec (1080p, 30 fps, loop on, debug off)") {
            val d = AppSettings.DEFAULT
            Assert.equals(ExportResolution.FULL_HD_1080, d.defaultResolution)
            Assert.equals(ExportFrameRate.FPS_30, d.defaultFrameRate)
            Assert.that(d.loopByDefault) { "clips should loop by default" }
            Assert.that(!d.debugOverlays) { "debug overlays must stay hidden for normal users" }
        },

        TestCase("malformed settings fall back to defaults instead of crashing") {
            Assert.equals(AppSettings.DEFAULT, AppSettingsCodec.decodeJsonOrNull("not json at all"))
            val partial = AppSettingsCodec.decode(
                com.rigstudio.core.json.Json.parse("""{"defaultResolution":"NOPE"}"""))
            Assert.equals(AppSettings.DEFAULT, partial) { "unknown enum must fall back" }
        },

        TestCase("settings seed a fresh export with the user's default resolution and fps") {
            val seed = AppSettings(
                defaultResolution = ExportResolution.HD_720,
                defaultFrameRate = ExportFrameRate.FPS_24,
            ).asExportSeed()
            Assert.equals(ExportResolution.HD_720, seed.resolution)
            Assert.equals(ExportFrameRate.FPS_24, seed.frameRate)
        },
    )
}
