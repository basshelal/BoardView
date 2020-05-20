package com.github.basshelal.boardview

import android.view.Choreographer

/**
 * Small utility class that allows for [doFrame] to be executed every frame based on the native
 * [Choreographer] instead of using precomputed values and timers.
 * [doFrame] is run on the main thread and is executed on the next frame.
 * The [SyncedRenderer] can be restarted freely.
 */
internal open class SyncedRenderer(val doFrame: (frameTimeNanos: Long) -> Unit) {

    private var callback: (Long) -> Unit = {}

    fun start() {
        callback = {
            doFrame(it)
            Choreographer.getInstance().postFrameCallback(callback)
        }
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        Choreographer.getInstance().removeFrameCallback(callback)
        callback = {}
    }
}

inline fun doOnceChoreographed(crossinline predicate: (frameTimeNanos: Long) -> Boolean,
                               crossinline onNextFrame: (frameTimeNanos: Long) -> Unit) {
    var callback: (Long) -> Unit = {}
    callback = {
        if (predicate(it)) {
            onNextFrame(it)
            Choreographer.getInstance().removeFrameCallback(callback)
            callback = {}
        } else Choreographer.getInstance().postFrameCallback(callback)
    }
    Choreographer.getInstance().postFrameCallback(callback)
}