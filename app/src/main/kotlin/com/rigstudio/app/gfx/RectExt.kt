package com.rigstudio.app.gfx

/**
 * On the real Android SDK, `android.graphics.Rect` / `RectF` expose `width()` / `height()` as
 * plain methods — there is no `getWidth()` / `getHeight()` JavaBean getter, so Kotlin synthesizes
 * no property and `rect.width` does not compile against the platform.
 *
 * The offline `tools/android-stubs` declare them as Kotlin properties for convenience, which is
 * why `tools/check_app.sh` passes either way (a member always shadows an extension). These
 * extensions keep the property syntax valid against the real SDK with identical values:
 * `width == right - left`, `height == bottom - top`.
 */
val android.graphics.RectF.width: Float
    get() = right - left

val android.graphics.RectF.height: Float
    get() = bottom - top

val android.graphics.Rect.width: Int
    get() = right - left

val android.graphics.Rect.height: Int
    get() = bottom - top
