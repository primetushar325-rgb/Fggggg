@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.os

class Bundle {
    fun getBoolean(key: String): Boolean = false
    fun getString(key: String): String? = null
}

class Build {
    class VERSION {
        companion object {
            @JvmField
            val SDK_INT: Int = 34
        }
    }

    class VERSION_CODES {
        companion object {
            const val O = 26
            const val O_MR1 = 27
            const val P = 28
            const val Q = 29
            const val R = 30
            const val S = 31
            const val TIRAMISU = 33
            const val N = 24
        }
    }
}

class Handler(val looper: Looper) {
    fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean = true
    fun removeCallbacks(runnable: Runnable) {}
    fun post(runnable: Runnable): Boolean = true
}

class Looper {
    companion object {
        @JvmStatic
        fun getMainLooper(): Looper = Looper()
    }
}

class Message

interface IBinder

object SystemClock {
    @JvmStatic
    fun elapsedRealtime(): Long = 0L
}

class PowerManager {
    fun isIgnoringBatteryOptimizations(packageName: String): Boolean = false
}

class Environment {
    companion object {
        const val DIRECTORY_DOWNLOADS = "Download"
        const val DIRECTORY_PICTURES = "Pictures"
        const val DIRECTORY_MOVIES = "Movies"
        const val DIRECTORY_MUSIC = "Music"
    }
}
