package com.github.basshelal.boardview

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class BoardContainerAdapter {

    /**
     * The [BoardViewContainer] that this adapter is attached to.
     */
    lateinit var boardViewContainer: BoardViewContainer
        internal set

    /**
     * The [RecyclerView.Adapter] that will be used by [BoardView], there will only exist a single
     * instance of this for any single [BoardViewContainer].
     */
    abstract fun getBoardViewAdapter(): BoardAdapter

    /**
     * Called when [BoardView] needs to create a new [BoardListAdapter] for a [BoardList] which
     * contains [BoardItemViewHolder]s and is responsible for displaying the items in each column.
     */
    abstract fun onCreateListAdapter(position: Int): BoardListAdapter<*>

    /**
     * Called when a new BoardView Column is created
     *
     * @param parentView the [ViewGroup] that contains the entire BoardView column
     * @return the header [View] or null if you do not want a header
     */
    abstract fun onCreateListHeader(parentView: ViewGroup): View?

    /**
     * Called when a new BoardView Column is created
     *
     * @param parentView the [ViewGroup] that contains the entire BoardView column
     * @return the footer [View] or null if you do not want a header
     */
    abstract fun onCreateFooter(parentView: ViewGroup): View?

    // Return true if they have successfully been swapped and false otherwise
    open fun onSwapBoardViewHolders(oldColumn: BoardColumnViewHolder, newColumn: BoardColumnViewHolder): Boolean = false

    // Return true if they have successfully been swapped and false otherwise
    open fun onSwapItemViewHolders(oldItem: BoardItemViewHolder, newItem: BoardItemViewHolder,
                                   oldColumn: BoardColumnViewHolder, newColumn: BoardColumnViewHolder): Boolean = false
}