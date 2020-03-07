@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.basshelal.boardview.drag.DragShadow
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*

/**
 * The container that will contain a [BoardView] as well as the [DragShadow]s for dragging
 * functionality of [BoardList]s and ItemViews, this is how we do dragging, if the caller only
 * wants a Board with no drag they use [BoardView]
 */
class BoardViewContainer
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var adapter: BoardContainerAdapter? = null
        set(value) {
            field = value
            boardView.adapter = value?.getBoardViewAdapter()
            value?.boardViewContainer = this
        }

    inline val boardView: BoardView get() = this._boardView
    inline val itemDragShadow: DragShadow get() = this.item_dragShadow
    inline val listDragShadow: DragShadow get() = this.list_dragShadow

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        itemDragShadow()
        listDragShadow()
    }

    private inline fun itemDragShadow() {
        itemDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

        }
    }

    private inline fun listDragShadow() {
        listDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

        }
    }

    /*
     * We should do all of the dragging shit here, not anywhere else!
     * Also I think we don't need to keep references to MotionEvents or obtain new ones if we're
     * smart about how we deal with onInterceptTouchEvent and onTouchEvent
     * */

    /*
     * For touch shit, in all cases we will still need a super call because that's how "default"
     * behavior is done, so we need to just know when it's our time to handle (dragging shit) and
     * when it's not we just return super (which will call it obviously)
     */

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // return true here to tell system that I will handle all subsequent touch events
        // they will thus not go down to my children in that case
        return true
    }

    // return false to tell system to stop sending events
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        //logE("TOUCH $now")
        val result: Boolean
        if (itemDragShadow.dragBehavior.dragState == ObservableDragBehavior.DragState.DRAGGING) {
            result = true
        } else {
            result = boardView.dispatchTouchEvent(event)
        }
        return result
    }
}


abstract class BoardContainerAdapter {

    lateinit var boardViewContainer: BoardViewContainer
        internal set

    // The single Board Adapter, we're only doing this so that it can be customizable by caller
    // and also have all the functionality of a RecyclerView.Adapter
    abstract fun getBoardViewAdapter(): BoardAdapter

    // When we need new List Adapters! These are simple RecyclerView Adapters that will display
    // the Items, this is the caller's responsibility as these can be reused from existing code
    abstract fun onCreateListAdapter(): BoardListAdapter<*>

    /**
     * Called when a new BoardView Column is created
     * @return the header View or null if you do not want a header
     */
    abstract fun onCreateListHeader(parentView: ViewGroup): View?
    abstract fun onCreateFooter(parentView: ViewGroup): View?

    // Touch and Drag Shit callbacks here TODO
}