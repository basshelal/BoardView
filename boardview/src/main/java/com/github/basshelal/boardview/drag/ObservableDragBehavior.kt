package com.github.basshelal.boardview.drag

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View

open class ObservableDragBehavior(view: View) : DragBehavior(view) {

    protected val initialTouchPoint = PointF()

    protected val dragListeners = ArrayList<DragListener>()

    open fun addDragListener(listener: DragListener) {
        dragListeners.add(listener)
        listener.onDragStateChanged(view, dragState)
    }

    open fun addDragListenerIfNotExists(listener: DragListener) {
        if (listener !in dragListeners) addDragListener(listener)
    }

    open fun removeDragListener(listener: DragListener) {
        dragListeners.remove(listener)
    }

    open fun clearDragListeners() = dragListeners.clear()

    var dragState: DragState = DragState.IDLE
        private set(value) {
            field = value
            dragListeners.forEach { it.onDragStateChanged(view, value) }
        }

    override fun onMove(event: MotionEvent) {
        super.onMove(event)
        dragListeners.forEach { it.onUpdateLocation(view, touchPoint) }
    }

    override fun animateReturn() {
        dragState = DragState.SETTLING
        super.animateReturn()
    }

    override fun afterEndAnimation() {
        super.afterEndAnimation()
        dragState = DragState.IDLE
        dragListeners.forEach { it.onEndDrag(view) }
    }

    override fun startDrag() {
        super.startDrag()
        initialTouchPoint.set(touchPoint)
        dragState = DragState.DRAGGING
        dragListeners.forEach { it.onStartDrag(view) }
    }

    override fun endDrag() {
        initialTouchPoint.set(0F, 0F)
        dragListeners.forEach { it.onReleaseDrag(view, touchPoint) }
        super.endDrag()
    }

    override fun startDragFromView(otherView: View) {
        dragState = DragState.DRAGGING
        dragListeners.forEach { it.onStartDrag(view) }
        super.startDragFromView(otherView)
    }

    enum class DragState {
        /** View is idle, no movement */
        IDLE,

        /** View is being dragged by user, movement from user */
        DRAGGING,

        /** View is settling into final position, movement is not from user */
        SETTLING
    }

    interface DragListener {

        /**
         * Called before the drag operation starts
         */
        fun onStartDrag(dragView: View)

        /**
         * Called when the drag touch location is updated, meaning when the user moves their
         * finger while dragging.
         *
         * Warning: This gets called **VERY OFTEN**, any code called in here should not be too
         * expensive, else you may experience jank while dragging.
         */
        fun onUpdateLocation(dragView: View, touchPoint: PointF)

        /**
         * Called when the user's touch is released. The [dragView] will start to animate its
         * return.
         */
        fun onReleaseDrag(dragView: View, touchPoint: PointF)

        /**
         * Called when the dragging operation has fully ended and the [dragView] has ended its
         * return animation.
         */
        fun onEndDrag(dragView: View)

        /**
         * Called when the [DragState] of the [dragView] is changed
         */
        fun onDragStateChanged(dragView: View, newState: DragState)

    }

    abstract class SimpleDragListener : DragListener {
        override fun onStartDrag(dragView: View) {}
        override fun onUpdateLocation(dragView: View, touchPoint: PointF) {}
        override fun onReleaseDrag(dragView: View, touchPoint: PointF) {}
        override fun onEndDrag(dragView: View) {}
        override fun onDragStateChanged(dragView: View, newState: DragState) {}
    }

}