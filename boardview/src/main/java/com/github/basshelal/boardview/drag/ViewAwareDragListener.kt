package com.github.basshelal.boardview.drag

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import com.github.basshelal.boardview.forEachReversed
import com.github.basshelal.boardview.globalVisibleRectF
import com.github.basshelal.boardview.parentViewGroup

// TODO: 20-Jun-20 Would be nice if we had one like this but that emitted events only when
//  entering a new View, and/or when user has given us an accepted View (they consumed the event
//  and want none more).
//  Basically an interface for users to have onEnteredNewView (or something like that)
abstract class ViewAwareDragListener : FrameSyncDragListener() {

    protected var dragView: View? = null
    protected var touchPoint: PointF = PointF()

    @CallSuper
    override fun onUpdateLocation(dragView: View, touchPoint: PointF) {
        this.dragView = dragView
        this.touchPoint = touchPoint
    }

    @CallSuper
    override fun onNextFrame(frameTimeNanos: Long) {
        dragView?.also { dragView ->
            var target = dragView.parentViewGroup?.childUnder(touchPoint, ignore = dragView)
            while (target != null) {
                onDragOverView(dragView, touchPoint, target)
                target = if (target is ViewGroup) target.childUnder(touchPoint, ignore = dragView) else null
            }
        }
    }

    override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
        frameSynchronizer.stop()
    }

    override fun onEndDrag(dragView: View) {
        super.onEndDrag(dragView)
        this.dragView = null
        this.touchPoint = PointF()
    }

    open fun onDragOverView(dragView: View, touchPoint: PointF, targetView: View?) {}

    @Suppress("NOTHING_TO_INLINE")
    protected inline fun ViewGroup.childUnder(pointF: PointF, ignore: View? = null): View? {
        val rect = this.globalVisibleRectF
        val x = pointF.x - rect.left
        val y = pointF.y - rect.top
        this.forEachReversed {
            if (it != ignore &&
                    x >= (it.left + it.translationX) &&
                    x <= (it.right + it.translationX) &&
                    y >= (it.top + it.translationY) &&
                    y <= (it.bottom + it.translationY)) {
                return it
            }
        }
        return null
    }
}