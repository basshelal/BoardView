package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.basshelal.boardview.drag.DragShadow

/**
 * The container that will contain [BoardView] as well as the [DragShadow]s for dragging
 * functionality of [BoardListView] and [ItemView]
 */
class BoardViewContainer
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr)

// TODO: 04-Mar-20 Draft API here!
abstract class BoardContainerAdapter<
        ItemVH : RecyclerView.ViewHolder,
        BoardListAdapter : RecyclerView.Adapter<ItemVH>,
        ListVH : RecyclerView.ViewHolder> {
    // either have 1 huge adapter for dealing with everything, or multiple adapters

    // =====List Items=====
    // onCreateListItem
    // onBindListItem
    // getListItemCount

    // =====BoardListViews=====
    // onCreateBoardListView
    // onBindBoardListView
    // getBoardListCount
}