package com.gamesoundpro.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class RecorderState { IDLE, RECORDING, PAUSED, STOPPED }

/**
 * Microphone recorder with start / pause / resume / stop, built on MediaRecorder (AAC in an
 * MP4 container). Files are written to app-private storage. The microphone is touched only
 * while the user is actively in the record flow — never in the background.
 */
class VoiceRecorder(private val context: Context) {

    private val _state = MutableStateFlow(RecorderState.IDLE)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /** UI polls this while recording to draw the level meter. */
    val amplitude: Int
        get() = try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }

    var outputFile: File? = null
        private set

    private var recorder: MediaRecorder? = null
    private var anchorRealtime = 0L
    private var accumulatedMs = 0L

    /** @return false when the mic is unavailable or busy with another app. */
    fun start(): Boolean {
        if (_state.value == RecorderState.RECORDING) return true
        return try {
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val file = dir.resolve("rec_${System.currentTimeMillis()}.m4a")
            val r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setAudioChannels(1)
            r.setMaxAmplitude(32767)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outputFile = file
            anchorRealtime = SystemClock.elapsedRealtime()
            accumulatedMs = 0L
            _state.value = RecorderState.RECORDING
            _elapsedMs.value = 0L
            true
        } catch (_: Exception) {
            releaseRecorder()
            false
        }
    }

    fun pause() {
        if (_state.value != RecorderState.RECORDING) return
        try {
            recorder?.pause()
            accumulatedMs += SystemClock.elapsedRealtime() - anchorRealtime
            _state.value = RecorderState.PAUSED
            _elapsedMs.value = accumulatedMs
        } catch (_: Exception) {
            releaseRecorder()
            _state.value = RecorderState.IDLE
        }
    }

    fun resume() {
        if (_state.value != RecorderState.PAUSED) return
        try {
            recorder?.resume()
            anchorRealtime = SystemClock.elapsedRealtime()
            _state.value = RecorderState.RECORDING
        } catch (_: Exception) {
            releaseRecorder()
            _state.value = RecorderState.IDLE
        }
    }

    /** Finalizes the recording; returns the file (or null when nothing usable was captured). */
    fun stop(): File? {
        if (_state.value != RecorderState.RECORDING && _state.value != RecorderState.PAUSED) return null
        val wasRecording = _state.value == RecorderState.RECORDING
        return try {
            recorder?.stop()
            if (wasRecording) {
                accumulatedMs += SystemClock.elapsedRealtime() - anchorRealtime
            }
            _elapsedMs.value = accumulatedMs
            _state.value = RecorderState.STOPPED
            val file = outputFile
            if (file != null && file.exists() && file.length() > 0) file else null
        } catch (_: Exception) {
            // stop() throws when no valid frames were captured.
            discard()
            null
        } finally {
            releaseRecorder()
        }
    }

    /** Throws away any captured audio and returns to idle. */
    fun discard() {
        releaseRecorder()
        outputFile?.let { if (it.exists()) it.delete() }
        outputFile = null
        accumulatedMs = 0L
        _elapsedMs.value = 0L
        _state.value = RecorderState.IDLE
    }

    /** Called from a UI ticker so [elapsedMs] stays fresh while recording. */
    fun tick() {
        if (_state.value == RecorderState.RECORDING) {
            _elapsedMs.value = accumulatedMs + (SystemClock.elapsedRealtime() - anchorRealtime)
        }
    }

    private fun newRecorder(): MediaRecorder =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }
}
