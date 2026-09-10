@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.view.animation

interface TimeInterpolator {
    fun getInterpolation(input: Float): Float
}

class OvershootInterpolator(tension: Float = 2f) : TimeInterpolator {
    override fun getInterpolation(input: Float): Float = input
}

class DecelerateInterpolator(factor: Float = 1f) : TimeInterpolator {
    override fun getInterpolation(input: Float): Float = input
}

class AccelerateInterpolator(factor: Float = 1f) : TimeInterpolator {
    override fun getInterpolation(input: Float): Float = input
}

class LinearInterpolator : TimeInterpolator {
    override fun getInterpolation(input: Float): Float = input
}
