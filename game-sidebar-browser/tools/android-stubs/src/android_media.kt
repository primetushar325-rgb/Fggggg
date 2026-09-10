@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.media

class AudioManager {
    fun getStreamMaxVolume(streamType: Int): Int = 15
    fun getStreamVolume(streamType: Int): Int = 7
    fun setStreamVolume(streamType: Int, index: Int, flags: Int) {}

    companion object {
        const val STREAM_MUSIC = 3
    }
}
