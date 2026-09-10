@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher

open class TextView(context: Context) : android.view.View(context) {
    var text: CharSequence? = null
    fun setTextColor(color: Int) {}
    /** The real getter is getCurrentTextColor(), not getTextColor() - so Kotlin sees no property. */
    fun getCurrentTextColor(): Int = 0
    var textSize: Float = 14f
    var maxLines: Int = Int.MAX_VALUE
    var minLines: Int = 1

    fun setHintTextColor(color: Int) {}
    fun getCurrentHintTextColor(): Int = 0
    /** Setter only - TextView has no getSingleLine(), so Kotlin sees no property. */
    fun setSingleLine(singleLine: Boolean) {}
    var gravity: Int = 0
    var typeface: android.graphics.Typeface? = null

    fun setTextSize(unit: Int, size: Float) {}
    fun setText(resId: Int) {}
}

class EditText(context: Context) : TextView(context) {
    val editableText: Editable? = null
    var hint: CharSequence? = null
    var imeOptions: Int = 0
    var inputType: Int = 0

    fun addTextChangedListener(watcher: TextWatcher) {}
    fun setOnEditorActionListener(listener: OnEditorActionListener) {}

    fun interface OnEditorActionListener {
        fun onEditorAction(v: TextView?, actionId: Int, event: android.view.KeyEvent?): Boolean
    }
    fun selectAll() {}

    companion object {
        const val IME_ACTION_GO = 2
        const val IME_ACTION_DONE = 6
    }
}

class ImageView(context: Context) : android.view.View(context) {
    fun setImageResource(resId: Int) {}
    fun setColorFilter(color: Int) {}
    fun setImageAlpha(alpha: Int) {}
    var scaleType: ScaleType? = null

    enum class ScaleType { FIT_CENTER, CENTER_CROP, CENTER_INSIDE }
}

open class LinearLayout(context: Context) : android.view.ViewGroup(context) {
    var orientation: Int = HORIZONTAL
    var gravity: Int = 0

    class LayoutParams(
        width: Int,
        height: Int,
        var weight: Float = 0f,
    ) : android.view.ViewGroup.MarginLayoutParams(width, height) {
        var gravity: Int = 0

        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }

    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
    }
}

open class FrameLayout(context: Context) : android.view.ViewGroup(context) {
    class LayoutParams(width: Int, height: Int) : android.view.ViewGroup.MarginLayoutParams(width, height) {
        var gravity: Int = 0

        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
}

class GridLayout(context: Context) : android.view.ViewGroup(context) {
    var columnCount: Int = 1
    var rowCount: Int = 1

    class Spec

    class LayoutParams : android.view.ViewGroup.MarginLayoutParams(WRAP_CONTENT, WRAP_CONTENT) {
        var columnSpec: Spec = Spec()
        var rowSpec: Spec = Spec()

        companion object {
            const val WRAP_CONTENT = -2
            const val MATCH_PARENT = -1
        }
    }

    companion object {
        const val UNDEFINED = -2147483648

        @JvmStatic
        fun spec(start: Int): Spec = Spec()

        @JvmStatic
        fun spec(start: Int, size: Int): Spec = Spec()

        @JvmStatic
        fun spec(start: Int, size: Int, weight: Float): Spec = Spec()
    }
}

open class HorizontalScrollView(context: Context) : android.view.ViewGroup(context)

open class ScrollView(context: Context) : android.view.ViewGroup(context)

class ProgressBar(context: Context) : android.view.View(context) {
    var progress: Int = 0
}

class SeekBar(context: Context) : android.view.View(context) {
    var progress: Int = 0
    var max: Int = 100
    fun setOnSeekBarChangeListener(listener: OnSeekBarChangeListener?) {}

    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
        fun onStartTrackingTouch(seekBar: SeekBar?) {}
        fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}

class PopupMenu(
    context: Context,
    anchor: android.view.View,
    gravity: Int = 0,
) {
    val menu: android.view.Menu = android.view.Menu()
    fun show() {}
    fun setOnMenuItemClickListener(listener: OnMenuItemClickListener?) {}

    fun interface OnMenuItemClickListener {
        fun onMenuItemClick(item: android.view.MenuItem): Boolean
    }
}

class Toast {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @JvmStatic
        fun makeText(context: Context, text: CharSequence, duration: Int): Toast = Toast()

        @JvmStatic
        fun makeText(context: Context, resId: Int, duration: Int): Toast = Toast()
    }

    fun show() {}
}
