@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.view

import android.content.Context

open class View(context: Context) {
    val context: Context = context
    val resources: android.content.res.Resources get() = context.resources
    var alpha: Float = 1f
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var translationX: Float = 0f
    var translationY: Float = 0f
    var visibility: Int = VISIBLE
    var isEnabled: Boolean = true
    var isClickable: Boolean = false
    var isFocusable: Boolean = false
    var contentDescription: CharSequence? = null
    val width: Int = 0
    val height: Int = 0
    var isVerticalScrollBarEnabled: Boolean = true
    var isHorizontalScrollBarEnabled: Boolean = true
    var overScrollMode: Int = 0
    var background: android.graphics.drawable.Drawable? = null
    var layoutParams: ViewGroup.LayoutParams? = null

    open fun onDraw(canvas: android.graphics.Canvas) {}
    open fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {}
    protected fun setMeasuredDimension(measuredWidth: Int, measuredHeight: Int) {}
    protected fun resolveSize(size: Int, measureSpec: Int): Int = size
    protected val suggestedMinimumWidth: Int = 0
    protected val suggestedMinimumHeight: Int = 0
    var minimumWidth: Int = 0
    var minimumHeight: Int = 0
    open fun onTouchEvent(event: MotionEvent): Boolean = false
    open fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    open fun setOnClickListener(listener: OnClickListener?) {}
    open fun setOnTouchListener(listener: OnTouchListener?) {}
    open fun setOnLongClickListener(listener: OnLongClickListener?) {}
    fun setOnFocusChangeListener(listener: OnFocusChangeListener) {}
    fun setOnKeyListener(listener: OnKeyListener) {}
    fun setOnScrollChangeListener(listener: OnScrollChangeListener) {}

    fun interface OnFocusChangeListener {
        fun onFocusChange(v: View, hasFocus: Boolean)
    }

    fun interface OnKeyListener {
        fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean
    }

    fun interface OnScrollChangeListener {
        fun onScrollChange(v: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int)
    }
    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {}
    fun setBackgroundResource(resId: Int) {}
    fun setBackgroundColor(color: Int) {}
    fun invalidate() {}
    fun requestFocus(): Boolean = true
    fun clearFocus() {}
    fun hasFocus(): Boolean = false
    fun performHapticFeedback(feedbackConstant: Int): Boolean = true
    fun postInvalidate() {}
    fun <T : View> requireViewById(id: Int): T = stubView() as T
    fun <T : View> findViewById(id: Int): T? = null
    fun animate(): ViewPropertyAnimator = ViewPropertyAnimator()
    fun addOnAttachStateChangeListener(listener: OnAttachStateChangeListener) {}
    val parent: ViewParent? get() = null

    fun interface OnClickListener {
        fun onClick(v: View)
    }

    fun interface OnTouchListener {
        fun onTouch(v: View, event: MotionEvent): Boolean
    }

    fun interface OnLongClickListener {
        fun onLongClick(v: View): Boolean
    }

    interface OnAttachStateChangeListener {
        fun onViewAttachedToWindow(v: View)
        fun onViewDetachedFromWindow(v: View)
    }

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
        const val OVER_SCROLL_IF_CONTENT_SCROLLS = 1
        const val OVER_SCROLL_NEVER = 2
    }
}

interface ViewParent

class ViewPropertyAnimator {
    fun alpha(value: Float): ViewPropertyAnimator = this
    fun scaleX(value: Float): ViewPropertyAnimator = this
    fun scaleY(value: Float): ViewPropertyAnimator = this
    fun translationX(value: Float): ViewPropertyAnimator = this
    fun translationY(value: Float): ViewPropertyAnimator = this
    fun setDuration(duration: Long): ViewPropertyAnimator = this
    fun setInterpolator(interpolator: android.view.animation.TimeInterpolator): ViewPropertyAnimator = this
    fun withEndAction(runnable: Runnable): ViewPropertyAnimator = this
    fun start() {}
}

open class ViewGroup(context: Context) : View(context) {
    open fun addView(child: View) {}
    open fun addView(child: View, index: Int) {}
    open fun addView(child: View, params: LayoutParams) {}
    open fun addView(child: View, index: Int, params: LayoutParams) {}
    open fun removeView(child: View) {}
    open fun removeViewImmediate(child: View) {}
    open fun removeAllViews() {}
    open fun getChildAt(index: Int): View? = null

    open class LayoutParams(var width: Int, var height: Int) {
        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }

    open class MarginLayoutParams(width: Int, height: Int) : LayoutParams(width, height) {
        fun setMargins(left: Int, top: Int, right: Int, bottom: Int) {}
    }
}

class KeyEvent(val action: Int, val keyCode: Int)

class MotionEvent {
    val action: Int = 0
    val actionMasked: Int = 0
    val rawX: Float = 0f
    val rawY: Float = 0f
    val x: Float = 0f
    val y: Float = 0f

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_OUTSIDE = 4
    }
}

class GestureDetector(context: Context, listener: OnGestureListener) {
    fun onTouchEvent(event: MotionEvent): Boolean = false

    interface OnGestureListener
    interface OnDoubleTapListener

    open class SimpleOnGestureListener : OnGestureListener, OnDoubleTapListener {
        open fun onSingleTapConfirmed(e: MotionEvent): Boolean = false
        open fun onDoubleTap(e: MotionEvent): Boolean = false
        open fun onLongPress(e: MotionEvent) {}
        open fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false
        open fun onDown(e: MotionEvent): Boolean = false
        open fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean = false
    }
}

class VelocityTracker {
    fun addMovement(event: MotionEvent) {}
    fun computeCurrentVelocity(units: Int) {}
    val xVelocity: Float = 0f
    val yVelocity: Float = 0f
    fun recycle() {}

    companion object {
        @JvmStatic
        fun obtain(): VelocityTracker = VelocityTracker()
    }
}

class ViewConfiguration {
    val scaledTouchSlop: Int = 24
    val scaledMinimumFlingVelocity: Int = 50

    companion object {
        @JvmStatic
        fun get(context: Context): ViewConfiguration = ViewConfiguration()
    }
}

object Gravity {
    const val TOP = 48
    const val BOTTOM = 80
    const val START = 8388611
    const val END = 8388613
    const val CENTER = 17
    const val CENTER_VERTICAL = 16
    const val CENTER_HORIZONTAL = 1
}

object HapticFeedbackConstants {
    const val LONG_PRESS = 1
    const val VIRTUAL_KEY = 0
}

class WindowInsets {
    fun getInsetsIgnoringVisibility(typeMask: Int): Insets = Insets()

    class Insets {
        val left: Int = 0
        val top: Int = 0
        val right: Int = 0
        val bottom: Int = 0
    }

    class Type {
        companion object {
            @JvmStatic
            fun systemBars(): Int = 0

            @JvmStatic
            fun displayCutout(): Int = 0
        }
    }
}

class Window {
    fun setType(type: Int) {}
    fun setBackgroundDrawable(drawable: android.graphics.drawable.Drawable?) {}
}

class WindowManager {
    fun addView(view: View, params: LayoutParams) {}
    fun updateViewLayout(view: View, params: LayoutParams) {}
    fun removeView(view: View) {}
    fun removeViewImmediate(view: View) {}
    val currentWindowMetrics: WindowMetrics = WindowMetrics()
    @Deprecated("Use WindowMetrics")
    val defaultDisplay: Display = Display()

    class WindowMetrics {
        val bounds: android.graphics.Rect = android.graphics.Rect()
        val windowInsets: WindowInsets = WindowInsets()
    }

    class Display {
        @Deprecated("Use WindowMetrics")
        fun getRealSize(outSize: android.graphics.Point) {}

        @Deprecated("Use WindowMetrics")
        fun getRectSize(outSize: android.graphics.Rect) {}
    }

    class LayoutParams(
        width: Int = MATCH_PARENT,
        height: Int = MATCH_PARENT,
        var type: Int = 0,
        var flags: Int = 0,
        var format: Int = 0,
    ) : ViewGroup.LayoutParams(width, height) {
        var gravity: Int = 0
        var x: Int = 0
        var y: Int = 0
        var softInputMode: Int = 0
        var screenBrightness: Float = -1f

        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
            const val TYPE_APPLICATION_OVERLAY = 2038
            const val TYPE_PHONE = 2002
            const val FLAG_NOT_FOCUSABLE = 8
            const val FLAG_NOT_TOUCH_MODAL = 32
            const val FLAG_WATCH_OUTSIDE_TOUCH = 262144
            const val FLAG_LAYOUT_NO_LIMITS = 512
            const val FLAG_HARDWARE_ACCELERATED = 16777216
            const val SOFT_INPUT_ADJUST_RESIZE = 16
        }
    }
}

class LayoutInflater {
    fun inflate(resource: Int, root: ViewGroup?, attachToRoot: Boolean): View = View(stubContext())

    companion object {
        @JvmStatic
        fun from(context: Context): LayoutInflater = LayoutInflater()
    }
}

private fun stubContext(): Context = StubContext()

private class StubContext : Context()

private fun stubView(): View = View(stubContext())
