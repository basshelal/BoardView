package com.github.basshelal.boardview.utils

import android.view.Choreographer
import androidx.annotation.UiThread

/**
 * Small utility class that allows for [onNextFrame] to be executed every frame based on the native
 * [Choreographer] instead of using precomputed values and timers.
 * [onNextFrame] is run on the UI thread and is executed on the next frame.
 * The [FrameSynchronizer] can be stopped and restarted.
 */
@UiThread
open class FrameSynchronizer(val onNextFrame: (frameTimeNanos: Long) -> Unit) {

    protected var callback: (Long) -> Unit = {}

    open fun start() {
        callback = {
            onNextFrame(it)
            Choreographer.getInstance().postFrameCallback(callback)
        }
        Choreographer.getInstance().postFrameCallback(callback)
    }

    open fun stop() {
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