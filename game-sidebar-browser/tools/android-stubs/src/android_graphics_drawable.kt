@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.graphics.drawable

import android.graphics.Canvas

abstract class Drawable {
    open fun mutate(): Drawable = this
    var alpha: Int
        get() = 255
        set(value) {}
    open fun draw(canvas: Canvas) {}
}

class GradientDrawable : Drawable() {
    var cornerRadius: Float
        get() = 0f
        set(value) {}

    fun setColor(color: Int) {}
}

class ColorDrawable(color: Int) : Drawable()
