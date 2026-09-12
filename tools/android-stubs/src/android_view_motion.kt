package android.view

/**
 * Minimal MotionEvent mirror for the offline stub compile (see tools/check_app.sh).
 * Only the surface RigStudio actually touches is modelled; values are inert.
 */
class MotionEvent private constructor() {

    val actionMasked: Int get() = 0
    val x: Float get() = 0f
    val y: Float get() = 0f
    val pointerCount: Int get() = 0
    val downTime: Long get() = 0L
    val eventTime: Long get() = 0L

    fun getPointerId(pointerIndex: Int): Int = 0
    fun findPointerIndex(pointerId: Int): Int = -1
    fun getX(pointerIndex: Int): Float = 0f
    fun getY(pointerIndex: Int): Float = 0f

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
    }
}
