package com.rigstudio.core.util

/**
 * A bounded undo/redo stack (V4 §28: the animation editor has Undo and Redo).
 *
 * The caller owns the state; the stack only stores snapshots of it:
 *
 *  1. before every mutating action call [record] with the state as it is *right now*;
 *  2. [undo] hands back the snapshot to restore and files the current state for redo;
 *  3. [redo] hands the redo snapshot back and files the current state for undo again.
 *
 * Recording after an undo clears the redo branch (the usual editor semantics: a new edit
 * invalidates the redo history). The stack is bounded — the oldest snapshot falls off — so
 * scrolling through an entire session cannot grow it without limit.
 */
class HistoryStack<T>(private val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Number of snapshots currently held (undo + redo); exposed for tests and diagnostics. */
    val size: Int get() = undoStack.size + redoStack.size

    /**
     * Files [stateBeforeChange] as an undo step. Call this *before* applying a mutation; the
     * snapshot must be immutable (or treated as such) because the stack keeps the reference.
     */
    fun record(stateBeforeChange: T) {
        undoStack.addLast(stateBeforeChange)
        if (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    /**
     * Pops the most recent undo snapshot. Returns `null` when there is nothing to undo — the
     * caller keeps its current state. [currentState] is filed onto the redo stack.
     */
    fun undo(currentState: T): T? {
        val snapshot = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(currentState)
        return snapshot
    }

    /**
     * Pops the most recent redo snapshot. Returns `null` when there is nothing to redo.
     * [currentState] is filed back onto the undo stack, so undo/redo remain symmetric.
     */
    fun redo(currentState: T): T? {
        val snapshot = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(currentState)
        return snapshot
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
