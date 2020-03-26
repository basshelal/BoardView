@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview.drag

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.github.basshelal.boardview.F
import com.github.basshelal.boardview.allChildren
import com.github.basshelal.boardview.globalVisibleRect
import com.github.basshelal.boardview.parentViewGroup
import org.jetbrains.anko.childrenRecursiveSequence
import org.jetbrains.anko.collections.forEachReversedByIndex
import kotlin.math.roundToInt

class DragView
@JvmOverloads
constructor(context: Context,
            attributeSet: AttributeSet? = null,
            defStyle: Int = 0
) : FrameLayout(context, attributeSet, defStyle) {

    private var dx = 0F
    private var dy = 0F

    val returnPoint = PointF()

    private var isDragging = false
    private var stealChildrenTouchEvents = false

    private var currentView: View? = null
    private var touchPoint = PointF()

    private var downCalled = false

    var touchPointOutOfParentBounds = false
        private set

    var onStateChanged: (DragState) -> Unit = { }
        set(value) {
            field = value
            onStateChanged(this.dragState)
        }

    var dragState: DragState = DragState.IDLE
        private set(value) {
            field = value
            onStateChanged(value)
        }

    var dragListener: DragListener? = null

    /**
     * The contents of the DragView or the first and only child of the DragView
     */
    inline var itemView: View?
        set(value) {
            removeAllViews()
            addView(value)
        }
        get() = getChildAt(0)

    override fun onFinishInflate() {
        super.onFinishInflate()
        returnPoint.set(this.x, this.y)
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        require(this.childCount <= 1) {
            "DragView does not allow more than 1 child View, wrap the children in a ViewGroup! " +
                    "current child count is $childCount"
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        touchPoint.set(event.rawX, event.rawY)
        return stealChildrenTouchEvents
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchPoint.set(event.rawX, event.rawY)
        if (isDragging) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onDown(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    onDown(event)
                    onMove(event)
                }
                MotionEvent.ACTION_UP -> {
                    endDrag()
                }
                else -> return super.onTouchEvent(event)
            }
            super.onTouchEvent(event)
            return true
        } else {
            return super.onTouchEvent(event)
        }
    }

    private inline fun onDown(event: MotionEvent) {
        if (!downCalled) {
            dx = this.x - event.rawX
            dy = this.y - event.rawY
            touchPoint.set(event.rawX, event.rawY)
            downCalled = true
        }
    }

    private inline fun onMove(event: MotionEvent) {
        this.x = event.rawX + dx
        this.y = event.rawY + dy
        touchPoint.set(event.rawX, event.rawY)
        dragListener?.onUpdateLocation(this, touchPoint)
        onEntered(event)
    }

    private inline fun onEntered(event: MotionEvent) {
        val newView = getViewUnder(event.rawX, event.rawY) ?: parentViewGroup
        if (currentView != newView) {
            dragListener?.onEnteredView(this, newView, currentView, touchPoint)
            currentView = newView
        }
        onBounds()
    }

    private inline fun onBounds() {
        val outOfBounds = !parentViewGroup.globalVisibleRect
                .contains(touchPoint.x.roundToInt(), touchPoint.y.roundToInt())
        if (touchPointOutOfParentBounds != outOfBounds) {
            touchPointOutOfParentBounds = outOfBounds
            if (touchPointOutOfParentBounds) {
                dragListener?.onExitedParentBounds(this, touchPoint)
            } else {
                dragListener?.onEnteredParentBounds(this, touchPoint)
            }
        }
    }

    fun getViewUnderTouchPoint(): View? = getViewUnder(touchPoint.x, touchPoint.y)

    private inline fun getViewUnder(pointX: Float, pointY: Float): View? {
        /* parentViewGroup.children.toList().forEachReversedByIndex {
             if (it.getGlobalVisibleRect.contains(pointX.roundToInt(), pointY.roundToInt())
                     && it != this && it !in this.childrenRecursiveSequence()) {
                 return it
             }
         }*/

        // Below for ALL Views that are descendants of my parent all the way to the bottom
        parentViewGroup.allChildren.forEachReversedByIndex {
            if (it.globalVisibleRect.contains(pointX.roundToInt(), pointY.roundToInt())
                    && it != this && it !in this.childrenRecursiveSequence()) {
                return it
            }
        }
        return null
    }

    private inline fun animateReturn() {
        dragListener?.onReleaseDrag(this, touchPoint)
        dragState = DragState.SETTLING

        val dampingRatio = 0.6F
        val stiffness = 1000F

        SpringAnimation(this, DynamicAnimation.X, returnPoint.x).also {
            it.spring.dampingRatio = dampingRatio
            it.spring.stiffness = stiffness
        }.start()
        SpringAnimation(this, DynamicAnimation.Y, returnPoint.y).also {
            it.spring.dampingRatio = dampingRatio
            it.spring.stiffness = stiffness
            it.addEndListener { _, _, _, _ -> afterEndAnimation() }
        }.start()
    }

    private inline fun afterEndAnimation() {
        currentView = null
        isDragging = false
        stealChildrenTouchEvents = false
        touchPointOutOfParentBounds = false
        downCalled = false
        dragState = DragState.IDLE
        dragListener?.onEndDrag(this)
    }

    fun setItemViewId(@LayoutRes itemViewId: Int) {
        itemView = LayoutInflater.from(context).inflate(itemViewId, parentViewGroup, false)
    }

    /**
     * Call this to start dragging from this DragView or any of its descendants, note that this
     * will "steal" those children's touch events while this DragView is being dragged.
     *
     * To start dragging from a View that is not a descendant of this DragView, use [startDragFromView]
     */
    fun startDrag() {
        returnPoint.set(this.x, this.y)
        dragState = DragState.DRAGGING
        dragListener?.onStartDrag(this)
        isDragging = true
        stealChildrenTouchEvents = true
    }

    /**
     * Call this to start dragging this DragView from another View that is not a descendant of
     * this View.
     *
     * Ideally the other [view] should be identical in appearance to this DragView
     */
    fun startDragFromView(view: View) {
        require(view in parentViewGroup.childrenRecursiveSequence()) {
            "The passed in view must be a descendant of this DragView's parent!"
        }
        dragState = DragState.DRAGGING
        dragListener?.onStartDrag(this)

        bringToFront()

        val parentBounds = parentViewGroup.globalVisibleRect
        val viewBounds = view.globalVisibleRect

        this.x = viewBounds.left.F - parentBounds.left.F
        this.y = viewBounds.top.F - parentBounds.top.F
        returnPoint.set(this.x, this.y)

        isDragging = true
        stealChildrenTouchEvents = true
        view.setOnTouchListener { v, event ->
            if (isDragging) {
                touchPoint.set(event.rawX, event.rawY)
                this.dispatchTouchEvent(event)
                v.onTouchEvent(event)
                v.parentViewGroup?.requestDisallowInterceptTouchEvent(true)
                true
            } else {
                false
            }
        }
    }

    /**
     * Call this to end the drag, this will start an animation to return this DragView to its
     * last acceptable drop location determined by the [dragListener]'s
     * [DragListener.onEnteredView] return value, if none was determined then this DragView will
     * return to its original location.
     */
    fun endDrag() {
        cancelLongPress()
        animateReturn()
    }

    /**
     * Call this to end the drag operation immediately, this is identical to [endDrag] except
     * without an animation
     */
    fun endDragNow() {
        cancelLongPress()
        dragListener?.onReleaseDrag(this, touchPoint)
        returnPoint.set(this.x, this.y)
        afterEndAnimation()
    }

    private inline val parentViewGroup: ViewGroup
        get() = this.parent as? ViewGroup?
                ?: throw IllegalStateException("Parent must be a non null ViewGroup" +
                        " parent is $parent")

    companion object {

        val longPressTime: Int
            get() = ViewConfiguration.getLongPressTimeout()

        fun fromView(view: View): DragView {
            return DragView(view.context).also { it.addView(view) }
        }
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
        fun onStartDrag(dragView: DragView)

        /**
         * Called when the drag touch location is updated, meaning when the user moves their
         * finger while dragging.
         *
         * Warning: This gets called **VERY OFTEN**, any code called in here should not be too
         * expensive, else you may experience jank while dragging. It is preferred to use
         * [onEnteredView] instead to receive events when the touch point enters a new view.
         */
        fun onUpdateLocation(dragView: DragView, touchPoint: PointF)

        /**
         * Called when the user's touch point enters a new View.
         *
         * The [newView] is the new view that the [touchPoint] has entered whereas [oldView] is
         * the view that the [touchPoint] has exited. [touchPoint] can never be over more than 1
         * view.
         *
         * The [newView] will always be a descendant of the [dragView]'s parent.
         * If there are multiple views stacked the topmost one under the touch point will be the
         * [newView].
         *
         * In most cases [oldView] will never be null and will instead be the [dragView]'s
         * parent, but this is rarely not the case.
         */
        fun onEnteredView(dragView: DragView, newView: View,
                          oldView: View?, touchPoint: PointF)

        /**
         * Called when the user's touch point exits the bounds of the [dragView]'s parent view.
         */
        fun onExitedParentBounds(dragView: DragView, touchPoint: PointF)

        /**
         * Called when the user's touch point re-enters the bounds of the [dragView]'s parent
         * view after it had exited
         */
        fun onEnteredParentBounds(dragView: DragView, touchPoint: PointF)

        /**
         * Called when the user's touch is release. The [dragView] will start to animate its return.
         */
        fun onReleaseDrag(dragView: DragView, touchPoint: PointF)

        /**
         * Called when the dragging operation has fully ended and the [dragView] has ended its
         * return animation.
         */
        fun onEndDrag(dragView: DragView)

    }

    abstract class SimpleDragListener : DragListener {
        override fun onStartDrag(dragView: DragView) {}
        override fun onUpdateLocation(dragView: DragView, touchPoint: PointF) {}
        override fun onEnteredView(dragView: DragView, newView: View,
                                   oldView: View?, touchPoint: PointF) {
        }

        override fun onExitedParentBounds(dragView: DragView, touchPoint: PointF) {}
        override fun onEnteredParentBounds(dragView: DragView, touchPoint: PointF) {}
        override fun onReleaseDrag(dragView: DragView, touchPoint: PointF) {}
        override fun onEndDrag(dragView: DragView) {}

        companion object {
            @Suppress("UNUSED_ANONYMOUS_PARAMETER")
            inline operator fun invoke(
                    crossinline onStartDrag: (dragView: DragView) -> Unit =
                            { dragView -> },
                    crossinline onUpdateLocation: (dragView: DragView,
                                                   touchPoint: PointF) -> Unit =
                            { dragView, touchPoint -> },
                    crossinline onEnteredView: (DragView, View,
                                                View?, PointF) -> Unit =
                            { dragView, newView, oldView, touchPoint -> },

                    crossinline onExitedParentBounds: (dragView: DragView,
                                                       touchPoint: PointF) -> Unit =
                            { dragView, touchPoint -> },
                    crossinline onEnteredParentBounds: (dragView: DragView,
                                                        touchPoint: PointF) -> Unit =
                            { dragView, touchPoint -> },
                    crossinline onReleaseDrag: (dragView: DragView,
                                                touchPoint: PointF) -> Unit =
                            { dragView, touchPoint -> },
                    crossinline onEndDrag: (dragView: DragView) -> Unit = { dragView -> }
            ): DragListener {
                return object : SimpleDragListener() {
                    override fun onStartDrag(dragView: DragView) =
                            onStartDrag(dragView)

                    override fun onUpdateLocation(dragView: DragView, touchPoint: PointF) =
                            onUpdateLocation(dragView, touchPoint)

                    override fun onEnteredView(dragView: DragView, newView: View,
                                               oldView: View?, touchPoint: PointF) =
                            onEnteredView(dragView, newView, oldView, touchPoint)

                    override fun onExitedParentBounds(dragView: DragView, touchPoint: PointF) =
                            onExitedParentBounds(dragView, touchPoint)

                    override fun onEnteredParentBounds(dragView: DragView, touchPoint: PointF) =
                            onEnteredParentBounds(dragView, touchPoint)

                    override fun onReleaseDrag(dragView: DragView, touchPoint: PointF) =
                            onReleaseDrag(dragView, touchPoint)

                    override fun onEndDrag(dragView: DragView) =
                            onEndDrag(dragView)
                }
            }
        }
    }

}