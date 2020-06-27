@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview.drag

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.github.basshelal.boardview.utils.F
import com.github.basshelal.boardview.utils.globalVisibleRect
import com.github.basshelal.boardview.utils.parentViewGroup
import org.jetbrains.anko.childrenRecursiveSequence

open class DragBehavior(val view: View) {

    protected val dPoint = PointF()
    protected val touchPoint = PointF()
    protected val returnPoint = PointF()

    protected var isDragging = false
    protected var stealChildrenTouchEvents = false

    private var downCalled = false

    private var touchPointOutOfParentBounds = false

    protected var dampingRatio = 0.6F
    protected var stiffness = 1000F

    init {
        returnPoint.set(view.x, view.y)
    }

    /**
     * This function will perform all of the dragging work for you, it is up to you to choose
     * where to call it.
     * You can place this inside your [View]'s [View.OnTouchListener] as follows:
     * ```
     * val dragBehavior = ObservableDragBehavior(myView)
     * myView.setOnTouchListener { v, event -> onTouchEvent(event) }
     * ```
     * Or if you are making your own Custom [View] just add it in your [View]'s
     * [View.onTouchEvent]. This way you can save your [View.OnTouchListener]. As follows:
     * ```
     * class MyView : View(...) {
     *     val dragBehavior = ObservableDragBehavior(this)
     *     ...
     *     override fun onTouchEvent(event: MotionEvent): Boolean {
     *         return super.onTouchEvent(event) || dragBehavior.onTouchEvent(event)
     *     }
     * }
     * ```
     *
     * @param event The [MotionEvent] that this [DragBehavior] will respond to
     * @return true if a drag event was performed and false otherwise, use this to determine if
     * the touch event was handled or not in [View.OnTouchListener] or [View.onTouchEvent]
     */
    open fun onTouchEvent(event: MotionEvent): Boolean {
        touchPoint.set(event.rawX, event.rawY)
        return if (isDragging) {
            view.parentViewGroup?.requestDisallowInterceptTouchEvent(true)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> onDown(event)
                MotionEvent.ACTION_MOVE -> onMove(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
            }
            true
        } else false
    }

    protected open fun onDown(event: MotionEvent) {
        if (!downCalled) {
            dPoint.x = view.x - event.rawX
            dPoint.y = view.y - event.rawY
            touchPoint.set(event.rawX, event.rawY)
            downCalled = true
        }
    }

    protected open fun onMove(event: MotionEvent) {
        onDown(event)
        view.x = event.rawX + dPoint.x
        view.y = event.rawY + dPoint.y
        touchPoint.set(event.rawX, event.rawY)
    }

    protected open fun animateReturn() {
        SpringAnimation(view, DynamicAnimation.X, returnPoint.x).also {
            it.spring.dampingRatio = dampingRatio
            it.spring.stiffness = stiffness
        }.start()
        SpringAnimation(view, DynamicAnimation.Y, returnPoint.y).also {
            it.spring.dampingRatio = dampingRatio
            it.spring.stiffness = stiffness
            it.addEndListener { _, _, _, _ -> afterEndAnimation() }
        }.start()
    }

    protected open fun afterEndAnimation() {
        isDragging = false
        stealChildrenTouchEvents = false
        touchPointOutOfParentBounds = false
        downCalled = false
    }

    open fun startDrag() {
        returnPoint.set(view.x, view.y)
        isDragging = true
        stealChildrenTouchEvents = true
    }

    open fun endDrag() {
        view.cancelLongPress()
        animateReturn()
    }

    open fun endDragNow() {
        view.cancelLongPress()
        returnPoint.set(view.x, view.y)
        afterEndAnimation()
    }

    open fun startDragFromView(otherView: View) {
        require(otherView in this.view.parentViewGroup!!.childrenRecursiveSequence()) {
            """"The passed in view must be a descendant of this DragView's parent! 
                Passed in View: $otherView 
                Parent: ${otherView.parent}"""
        }

        this.view.bringToFront()

        val parentBounds = this.view.parentViewGroup!!.globalVisibleRect
        val viewBounds = otherView.globalVisibleRect

        this.view.x = viewBounds.left.F - parentBounds.left.F
        this.view.y = viewBounds.top.F - parentBounds.top.F
        startDrag()
    }

    open fun returnTo(view: View) {
        val parentBounds = this.view.parentViewGroup!!.globalVisibleRect
        val viewBounds = view.globalVisibleRect

        returnPoint.x = viewBounds.left.F - parentBounds.left.F
        returnPoint.y = viewBounds.top.F - parentBounds.top.F
    }

}