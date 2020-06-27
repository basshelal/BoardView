package com.github.basshelal.boardview.drag

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import com.github.basshelal.boardview.utils.forEachReversed
import com.github.basshelal.boardview.utils.globalVisibleRectF
import com.github.basshelal.boardview.utils.parentViewGroup

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