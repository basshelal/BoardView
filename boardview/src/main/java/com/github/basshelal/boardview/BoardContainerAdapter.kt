package com.github.basshelal.boardview

import android.view.View
import android.view.ViewGroup

abstract class BoardContainerAdapter {

    lateinit var boardViewContainer: BoardViewContainer
        internal set

    // The single Board Adapter, we're only doing this so that it can be customizable by caller
    // and also have all the functionality of a RecyclerView.Adapter
    abstract fun getBoardViewAdapter(): BoardAdapter

    // When we need new List Adapters! These are simple RecyclerView Adapters that will display
    // the Items, this is the caller's responsibility as these can be reused from existing code
    abstract fun onCreateListAdapter(position: Int): BoardListAdapter<*>

    /**
     * Called when a new BoardView Column is created
     * @return the header View or null if you do not want a header
     */
    abstract fun onCreateListHeader(parentView: ViewGroup): View?
    abstract fun onCreateFooter(parentView: ViewGroup): View?

    // Return true when the given boardListAdapter is correct for the position, false otherwise
    abstract fun matchListAdapter(boardListAdapter: BoardListAdapter<*>, position: Int): Boolean

    // Touch and Drag Shit callbacks here TODO

    open fun onSwapBoardViewHolders(old: BoardViewColumnVH, new: BoardViewColumnVH) {}
}