package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.github.basshelal.boardview.drag.DragShadow
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*

/**
 * The container that will contain a [BoardView] as well as the [DragShadow]s for dragging
 * functionality of [BoardList]s and ItemViews, this is how we do dragging, if the caller only
 * wants a Board with no drag they use [BoardView]
 */
class BoardViewContainer
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var adapter: BoardContainerAdapter? = null

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        boardView.adapter = BoardAdapter()
        //boardView.adapter = adapter?.getBoardViewAdapter()
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
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // return false to tell system to stop sending events
        return super.onTouchEvent(event)
    }
}


// TODO: 04-Mar-20 Draft API here!
abstract class BoardContainerAdapter {

    // The single Board Adapter, we're only doing this so that it can be customizable by caller
    // and also have all the functionality of a RecyclerView.Adapter
    abstract fun getBoardViewAdapter(): BoardAdapter

    // When we need new List Adapters! These are simple RecyclerView Adapters that will display
    // the Items, this is the caller's responsibility as these can be reused from existing code
    abstract fun onCreateListAdapter(position: Int): BoardListAdapter<*>

    // When we create BoardColumns

    // return null if you don't want a header or footer
    abstract fun onCreateListHeader(position: Int): View?
    abstract fun onCreateFooter(position: Int): View?
}