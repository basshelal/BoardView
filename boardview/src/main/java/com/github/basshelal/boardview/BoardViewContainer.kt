@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.github.basshelal.boardview.drag.DragShadow
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import com.github.basshelal.boardview.drag.ObservableDragBehavior.DragState.DRAGGING
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

    /**
     * The [BoardContainerAdapter] of this [BoardViewContainer].
     * This is the main API entry point, callers implement their own [BoardContainerAdapter] and
     * set the adapter of this [BoardViewContainer] created in a layout file to an instance of
     * their custom [BoardContainerAdapter].
     *
     * @see BoardContainerAdapter
     */
    public var adapter: BoardContainerAdapter? = null
        set(value) {
            field = value
            boardView.adapter = value?.boardViewAdapter
            value?.boardViewContainer = this
        }

    /**
     * The [BoardView] this container holds.
     * This is the [RecyclerView] that draws and holds the [BoardColumnViewHolder]s
     * @see BoardView
     */
    public inline val boardView: BoardView get() = this._boardView

    /**
     * The [DragShadow] that appears when dragging [BoardItemViewHolder]s.
     * This is a simple shadow that resembles the original [BoardItemViewHolder]
     * but can be dragged outside the bounds of the [BoardView]
     * @see DragShadow
     */
    public inline val itemDragShadow: DragShadow get() = this.item_dragShadow

    /**
     * The [DragShadow] that appears when dragging [BoardColumnViewHolder]s.
     * This is a simple shadow that resembles the original [BoardColumnViewHolder]
     * but can be dragged outside the bounds of the [BoardView]
     * @see DragShadow
     */
    public inline val listDragShadow: DragShadow get() = this.list_dragShadow

    //region Private variables

    /** The current touch point, updated in [onTouchEvent] */
    private var touchPointF = PointF()

    /** The current [DraggingItem] when dragging [itemDragShadow] */
    private val draggingItem = DraggingItem()

    /** The current [BoardColumnViewHolder] when dragging [listDragShadow] */
    private var draggingColumnVH: BoardColumnViewHolder? = null

    /** The pending [ViewHolderSwap]s for [BoardColumnViewHolder]s when dragging [listDragShadow] */
    private val pendingColumnVHSwaps = LinkedHashSet<ViewHolderSwap>()

    /** The pending [ViewHolderSwap]s for [BoardItemViewHolder]s when dragging [itemDragShadow] */
    private val pendingItemVHSwaps = LinkedHashSet<ViewHolderSwap>()

    //endregion Private variables

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        initializeItemDragShadow()
        initializeListDragShadow()
    }

    /**
     * Used to tell [BoardViewContainer] to begin dragging [itemDragShadow]
     * and to make it mirror [boardItemViewHolder]
     */
    public inline fun startDraggingItem(boardItemViewHolder: BoardItemViewHolder) {
        itemDragShadow.rootUpdateToMatch(boardItemViewHolder.itemView)
        itemDragShadow.dragBehavior.startDrag()
    }

    /**
     * Used to tell [BoardViewContainer] to begin dragging [listDragShadow]
     * and to make it mirror [boardColumnViewHolder]
     */
    public inline fun startDraggingColumn(boardColumnViewHolder: BoardColumnViewHolder) {
        listDragShadow.rootUpdateToMatch(boardColumnViewHolder.itemView)
        listDragShadow.dragBehavior.startDrag()
    }

    /** Return `true` to tell system that we will handle all touch events in [onTouchEvent] */
    override fun onInterceptTouchEvent(event: MotionEvent) = true

    /**
     * Handle touch events and set [touchPointF]
     * Unless user is dragging, all events are forwarded to [boardView]
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchPointF.set(event.rawX, event.rawY)
        return when {
            // Item is being dragged, send all events to its onTouchEvent
            itemDragShadow.dragBehavior.dragState == DRAGGING ->
                itemDragShadow.dragBehavior.onTouchEvent(event)
            // List is being dragged, send all events to its onTouchEvent
            listDragShadow.dragBehavior.dragState == DRAGGING ->
                listDragShadow.dragBehavior.onTouchEvent(event)
            // Send all other events to BoardView (scrolling etc)
            else -> boardView.dispatchTouchEvent(event)
        }
    }

    //region Private functions

    @CalledOnce
    private inline fun initializeItemDragShadow() {
        itemDragShadow.dragBehavior.addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {

            /**
             * Scrolls the [boardView] depending on position of [touchPointF]
             * Executes every frame, courtesy of [SyncedRenderer]
             */
            private val scroller = SyncedRenderer {
                boardView.horizontalScroll(touchPointF)
                draggingItem.columnViewHolder?.also { it.list?.verticalScroll(touchPointF) }
            }

            /**
             * Swaps the [BoardItemViewHolder]s depending on position of [touchPointF]
             * This does it for both, inside the same list and across lists
             * Executes every frame, courtesy of [SyncedRenderer]
             */
            private val swapper = SyncedRenderer {
                draggingItem.also { draggingColumnVH, draggingItemVH ->
                    findItemViewHolderUnder(touchPointF).also { columnVH, itemVH ->
                        swapItemViewHolders(draggingItemVH, itemVH, draggingColumnVH, columnVH)
                    }
                }
            }

            /** User has started drag, initialize everything */
            override fun onStartDrag(dragView: View) {
                val (column, item) = findItemViewHolderUnder(touchPointF)
                draggingItem.columnViewHolder = column
                draggingItem.itemViewHolder = item
                itemDragShadow.isVisible = true
                draggingItem.itemViewHolder?.itemView?.alpha = 0F
                scroller.start()
                swapper.start()
            }

            /** User has released drag, animation is starting */
            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                scroller.stop()
                swapper.stop()
            }

            /** Animation has ended, drag is finished, finalize everything */
            override fun onEndDrag(dragView: View) {
                draggingItem.itemViewHolder?.itemView?.also {
                    itemDragShadow.dragBehavior.returnTo(it)
                    it.alpha = 1F
                }
                itemDragShadow.isVisible = false
                draggingItem.itemViewHolder = null
                draggingItem.columnViewHolder = null
                pendingItemVHSwaps.clear()
            }
        })
    }

    @CalledOnce
    private inline fun initializeListDragShadow() {
        listDragShadow.dragBehavior.addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {

            private val scroller = SyncedRenderer {
                boardView.horizontalScroll(touchPointF)
            }

            private val swapper = SyncedRenderer {
                draggingColumnVH?.also { draggingColumnVH ->
                    boardView.getViewHolderUnder(touchPointF)?.also { newVH ->
                        swapColumnViewHolders(draggingColumnVH, newVH)
                    }
                }
            }

            /** User has started drag, initialize everything */
            override fun onStartDrag(dragView: View) {
                draggingColumnVH = boardView.getViewHolderUnder(touchPointF)
                listDragShadow.isVisible = true
                draggingColumnVH?.itemView?.alpha = 0F
                scroller.start()
                swapper.start()
            }

            /** User has released drag, animation is starting */
            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                scroller.stop()
                swapper.stop()
            }

            /** Animation has ended, drag is finished, finalize everything */
            override fun onEndDrag(dragView: View) {
                draggingColumnVH?.itemView?.also {
                    listDragShadow.dragBehavior.returnTo(it)
                    it.alpha = 1F
                }
                listDragShadow.isVisible = false
                draggingColumnVH = null
                pendingColumnVHSwaps.clear()
            }
        })
    }

    @CalledOnce
    private inline fun swapItemViewHolders(oldItemVH: BoardItemViewHolder, newItemVH: BoardItemViewHolder,
                                           oldColumnVH: BoardColumnViewHolder, newColumnVH: BoardColumnViewHolder) {
        if (oldItemVH != newItemVH
                && oldItemVH.isAdapterPositionValid && newItemVH.isAdapterPositionValid &&
                oldColumnVH.isAdapterPositionValid && newColumnVH.isAdapterPositionValid &&
                boardView.itemAnimator?.isRunning != true &&
                oldColumnVH.list?.itemAnimator?.isRunning != true &&
                newColumnVH.list?.itemAnimator?.isRunning != true) {
            val swap = ViewHolderSwap(oldItemVH, newItemVH)
            pendingItemVHSwaps.add(swap)
            if (swap in pendingItemVHSwaps && !swap.hasSwapped) {
                if (adapter?.onSwapItemViewHolders(oldItemVH, newItemVH, oldColumnVH, newColumnVH) == true) {
                    notifyItemViewHoldersSwapped(oldItemVH, newItemVH, oldColumnVH, newColumnVH)
                    itemDragShadow.dragBehavior.returnTo(newItemVH.itemView)
                }
                swap.hasSwapped = true
                pendingItemVHSwaps.remove(swap)
            }
        }
    }

    @CalledOnce
    private inline fun notifyItemViewHoldersSwapped(oldItemVH: BoardItemViewHolder, newItemVH: BoardItemViewHolder,
                                                    oldColumnVH: BoardColumnViewHolder, newColumnVH: BoardColumnViewHolder) {
        val fromItem = oldItemVH.adapterPosition
        val toItem = newItemVH.adapterPosition
        val fromColumn = oldColumnVH.adapterPosition
        val toColumn = newColumnVH.adapterPosition

        logE("Swapping from column $fromColumn to column $toColumn " +
                " item $fromItem to $toItem")

        when {
            // They are in the same list
            fromColumn == toColumn -> when {
                // They are the same
                fromItem == toItem -> return
                // They are different
                fromItem != toItem ->
                    oldColumnVH.list?.notifyItemViewHoldersSwapped(oldItemVH, newItemVH)
            }
            // They are in different lists
            fromColumn != toColumn -> {
                oldColumnVH.boardListAdapter?.notifyItemRemoved(fromItem)
                newColumnVH.boardListAdapter?.notifyItemInserted(toItem)
                doOnceChoreographed({
                    oldColumnVH.list?.itemAnimator?.isRunning != true &&
                            newColumnVH.list?.itemAnimator?.isRunning != true
                }) {
                    draggingItem.itemViewHolder = newColumnVH.list
                            ?.findViewHolderForAdapterPosition(toItem) as? BoardItemViewHolder
                    draggingItem.itemViewHolder?.itemView?.alpha = 0F
                    draggingItem.columnViewHolder = newColumnVH
                }
            }
        }

    }

    @CalledOnce
    private inline fun swapColumnViewHolders(oldVH: BoardColumnViewHolder, newVH: BoardColumnViewHolder) {
        // VHs must be valid, different and Board is not animating anything
        if (newVH != oldVH && boardView.itemAnimator?.isRunning != true &&
                oldVH.isAdapterPositionValid && newVH.isAdapterPositionValid) {
            val swap = ViewHolderSwap(oldVH, newVH)
            pendingColumnVHSwaps.add(swap) // Will only be added if it doesn't exist already
            if (swap in pendingColumnVHSwaps && !swap.hasSwapped) { // VHSwap has been queued but not executed, perform swap!
                if (adapter?.onSwapBoardViewHolders(oldVH, newVH) == true) { // Caller has told us the swap is successful, let's animate the swap!
                    boardView.notifyColumnViewHoldersSwapped(oldVH, newVH)
                    listDragShadow.dragBehavior.returnTo(newVH.itemView)
                }
                // Remove the swap in any case, its life is over
                swap.hasSwapped = true
                pendingColumnVHSwaps.remove(swap)
            }
        }
    }

    private fun findItemViewHolderUnder(point: PointF): DraggingItem {
        var boardVH: BoardColumnViewHolder? = null
        var itemVH: BoardItemViewHolder? = null
        boardView.getViewHolderUnder(point)?.also {
            boardVH = it
            it.list?.getViewHolderUnder(point)?.also {
                itemVH = it
            } ?: kotlin.run {
                // We are not over a VH
                logE("NOT OVER VH $now")
                // TODO: 23-May-20 Here is when we are over empty part of non full list or
                //  completely empty list
                //  we already know which list we are over, we just need to handle the action
                //  correctly depending on whether the list has elements or not
                //  Can't do adapter changes here :/ we need to return details used to modify :/
                boardVH?.boardListAdapter?.lastPosition?.let {
                    itemVH = (if (it > 0) boardVH?.list?.findViewHolderForAdapterPosition(it) else null)
                            as? BoardItemViewHolder
                }
            }
        }
        return DraggingItem(boardVH, itemVH)
    }

    //endregion Private functions

    companion object {
        internal val MAX_POOL_COUNT = 25
        internal val ITEM_VH_POOL = object : RecyclerView.RecycledViewPool() {
            override fun setMaxRecycledViews(viewType: Int, max: Int) =
                    super.setMaxRecycledViews(viewType, MAX_POOL_COUNT)
        }
    }

}

/** Represents a ViewHolder Swap which we can use to track if swaps are completed */
private data class ViewHolderSwap(
        val oldPosition: Int,
        val newPosition: Int,
        val oldId: Long,
        val newId: Long) {

    var hasSwapped: Boolean = false

    constructor(old: RecyclerView.ViewHolder, new: RecyclerView.ViewHolder) :
            this(old.adapterPosition, new.adapterPosition, old.itemId, new.itemId)

    override fun toString(): String {
        return "(oldPos: $oldPosition, " +
                "newPos: $newPosition, " +
                "oldId: $oldId, " +
                "newId: $newId, " +
                "hasSwapped: $hasSwapped)"
    }
}

/** Pair of [BoardColumnViewHolder] and [BoardItemViewHolder], can be used for anything really */
private data class DraggingItem(
        var columnViewHolder: BoardColumnViewHolder? = null,
        var itemViewHolder: BoardItemViewHolder? = null) {

    /** Executes [block] only if both [columnViewHolder] and [itemViewHolder] are not `null` */
    inline fun also(block: (columnViewHolder: BoardColumnViewHolder,
                            itemViewHolder: BoardItemViewHolder) -> Unit): DraggingItem {
        columnViewHolder?.also { columnViewHolder ->
            itemViewHolder?.also { itemViewHolder ->
                block(columnViewHolder, itemViewHolder)
            }
        }
        return this
    }
}