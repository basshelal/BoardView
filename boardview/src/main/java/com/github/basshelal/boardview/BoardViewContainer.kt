package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.basshelal.boardview.drag.DragShadow
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*

/**
 * The container that will contain [BoardView] as well as the [DragShadow]s for dragging
 * functionality of [BoardListView] and [ItemView]
 */
class BoardViewContainer
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        boardView.adapter = BoardViewAdapter()
    }

    /*
     * We should do all of the dragging shit here, not anywhere else!
     * Also I think we don't need to keep references to MotionEvents or obtain new ones if we're
     * smart about how we deal with onInterceptTouchEvent and onTouchEvent
     * */

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return super.onTouchEvent(event)
    }
}


// TODO: 04-Mar-20 Draft API here!
abstract class BoardContainerAdapter<
        ItemVH : RecyclerView.ViewHolder,
        BoardListAdapter : RecyclerView.Adapter<ItemVH>,
        ListVH : RecyclerView.ViewHolder> {
    // either have 1 huge adapter for dealing with everything, or multiple adapters

    // =====BoardListViews=====
    // onCreateBoardListView
    // onBindBoardListView
    // getBoardListCount

    // When we need to create a new ListAdapter
    // onCreateListAdapter(position: Int): BoardListViewAdapter


    // When we need new List Adapters
    abstract fun onCreateListAdapter(position: Int): BoardListViewAdapter
}