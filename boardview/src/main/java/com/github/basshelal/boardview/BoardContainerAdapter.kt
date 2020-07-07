package com.github.basshelal.boardview

import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.github.basshelal.boardview.utils.Beta

/**
 * This is the entry point for the BoardView library.
 *
 * Extend this class with your own implementations to the various variables and callbacks that
 * will change the way a [BoardViewContainer] behaves, then in your [BoardViewContainer] set its
 * [BoardViewContainer.adapter] to be your new custom adapter.
 */
abstract class BoardContainerAdapter {

    /**
     * The [BoardViewContainer] that this adapter is attached to.
     */
    lateinit var boardViewContainer: BoardViewContainer
        internal set

    // TODO: 28-Jun-20 Remove animations are not nice see a solution idea here
    //  https://stackoverflow.com/questions/38243398
    //  the main idea is that we animate the list going up while the items are animating removal
    // TODO: 28-Jun-20 Document!
    @Beta
    open val isListWrapContent: Boolean = false

    // TODO: 01-Jul-20 Document!
    abstract val headerLayoutRes: Int?

    // TODO: 01-Jul-20 Document!
    abstract val footerLayoutRes: Int?

    /**
     * If true, means that the first item of the list will always be visible and not overlap with
     * the header.
     *
     * Defaults to `true`, meaning the header does not overlap with the list.
     */
    open val isHeaderPadded: Boolean = true

    /**
     * If true, means that the last item of the list will always be visible and not overlap with
     * the footer.
     *
     * Defaults to `true`, meaning the footer does not overlap with the list.
     */
    open val isFooterPadded: Boolean = true

    /**
     * The [BoardAdapter] instance that [boardViewContainer]'s [BoardView] will use as its adapter,
     * there will only exist a single instance of this for any single [BoardViewContainer].
     *
     * This will only ever be called once so it is safe to return a new
     * [BoardAdapter] in the `get()` implementation
     */
    abstract val boardViewAdapter: BoardAdapter<*>

    /**
     * Called when [BoardView] needs to create a new [BoardListAdapter] for a [BoardList].
     *
     * @param position the position of the [BoardList] that needs a new [BoardListAdapter]
     * @return a new [BoardListAdapter] for the [BoardList] at [position]
     */
    abstract fun onCreateListAdapter(position: Int): BoardListAdapter<*>

    /**
     * Called when the user is dragging the [draggingColumn] over the [targetPosition].
     *
     * This is where you should change your data contents to reflect the move.
     *
     * Return `true` to allow the move to happen, a `false` return value will effectively
     * disallow the move to be made.
     *
     * Do not call any `notify...()` methods here,
     * these are handled internally in [BoardViewContainer]
     *
     * @param draggingColumn the [BoardColumnViewHolder] that the user is dragging
     * @param targetPosition the position the [draggingColumn] is over and is requesting to move
     * to, no guarantee can be made that this will not be [NO_POSITION]
     * @return `true` to allow the move, `false` to disallow it
     */
    open fun onMoveColumn(draggingColumn: BoardColumnViewHolder,
                          targetPosition: Int): Boolean = false

    /**
     * Called when the user is dragging the [draggingItem] from the [draggingColumn] over the
     * [targetPosition] in the [targetColumn].
     *
     * This is where you should change your data contents to reflect the move.
     *
     * Note that it is possible that [draggingColumn] and [targetColumn] be equal which would
     * mean that the [draggingItem] is being dragged within the same [BoardColumnViewHolder].
     *
     * Return `true` to allow the move to happen, a `false` return value will effectively
     * disallow the move to be made.
     *
     * **DO NOT call any `notify...()` methods here,
     * these are handled internally in [BoardViewContainer]**
     *
     * @param draggingItem the [BoardItemViewHolder] that the user is dragging
     * @param targetPosition the position the [draggingItem] is over in the [draggingColumn] and
     * is requesting to move to, no guarantee can be made that this will not be [NO_POSITION]
     * @param draggingColumn the [BoardColumnViewHolder] that the [draggingItem] is from
     * @param targetColumn the [BoardColumnViewHolder] that the [draggingItem] is over, this can
     * be equal to the [draggingColumn] which means that the move is within the same [BoardColumnViewHolder]
     * @return `true` to allow the move, `false` to disallow it
     *
     */
    open fun onMoveItem(draggingItem: BoardItemViewHolder, targetPosition: Int,
                        draggingColumn: BoardColumnViewHolder, targetColumn: BoardColumnViewHolder): Boolean = false
}