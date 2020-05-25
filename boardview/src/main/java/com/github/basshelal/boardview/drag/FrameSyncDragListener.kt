package com.github.basshelal.boardview.drag

import android.graphics.PointF
import android.view.View
import androidx.annotation.CallSuper
import com.github.basshelal.boardview.FrameSynchronizer

abstract class FrameSyncDragListener : ObservableDragBehavior.DragListener {

    protected val frameSynchronizer = FrameSynchronizer { onNextFrame(it) }

    @CallSuper
    override fun onStartDrag(dragView: View) {
        frameSynchronizer.start()
    }

    open fun onNextFrame(frameTimeNanos: Long) {}

    @CallSuper
    override fun onEndDrag(dragView: View) {
        frameSynchronizer.stop()
    }

    override fun onUpdateLocation(dragView: View, touchPoint: PointF) {}
    override fun onReleaseDrag(dragView: View, touchPoint: PointF) {}
    override fun onDragStateChanged(dragView: View, newState: ObservableDragBehavior.DragState) {}
}

abstract class MultipleFrameSyncDragListener : ObservableDragBehavior.DragListener {

    protected val frameSynchronizers = ArrayList<FrameSynchronizer>()

    @CallSuper
    override fun onStartDrag(dragView: View) {
        frameSynchronizers.forEach { it.start() }
    }

    @CallSuper
    override fun onEndDrag(dragView: View) {
        frameSynchronizers.forEach { it.stop() }
    }

    fun addCallback(callback: (frameTimeNanos: Long) -> Unit) {
        frameSynchronizers.add(FrameSynchronizer(callback))
    }

    override fun onUpdateLocation(dragView: View, touchPoint: PointF) {}
    override fun onReleaseDrag(dragView: View, touchPoint: PointF) {}
    override fun onDragStateChanged(dragView: View, newState: ObservableDragBehavior.DragState) {}
}