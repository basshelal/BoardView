@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.github.basshelal.boardview.drag.DragShadow
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import com.github.basshelal.boardview.drag.ObservableDragBehavior.DragState.DRAGGING
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*
import kotlin.math.floor

/**
 * The container that will contain a [BoardView] as well as the [DragShadow]s for dragging
 * functionality of [BoardList]s and ItemViews, this is how we do dragging, if the caller only
 * wants a Board with no drag they use [BoardView]
 */
class BoardViewContainer
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    public var adapter: BoardContainerAdapter? = null
        set(value) {
            field = value
            boardView.adapter = value?.boardViewAdapter
            value?.boardViewContainer = this
        }

    public inline val boardView: BoardView get() = this._boardView
    public inline val itemDragShadow: DragShadow get() = this.item_dragShadow
    public inline val listDragShadow: DragShadow get() = this.list_dragShadow

    // Current Touch Point
    private var touchPointF = PointF()

    private val draggingItem = DraggingItem()

    private var draggingColumnVH: BoardColumnViewHolder? = null

    // We update observers based on the screen refresh rate because animations are not able to
    // keep up with a faster update rate
    private val updateRatePerMilli = floor(millisPerFrame)

    // Animation stuff
    private val pendingColumnVHSwaps = LinkedHashSet<ViewHolderSwap>()
    private val pendingItemVHSwaps = LinkedHashSet<ViewHolderSwap>()

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        initializeItemDragShadow()
        initializeListDragShadow()
    }

    private inline fun initializeItemDragShadow() {
        itemDragShadow.dragBehavior.addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {

            private val scroller = SyncedRenderer {
                boardView.horizontalScroll(touchPointF)
                draggingItem.columnViewHolder?.also { draggingColumnVH ->
                    draggingColumnVH.list?.verticalScroll(touchPointF)
                }
            }

            private val swapper = SyncedRenderer {
                draggingItem.also { draggingColumnVH, draggingItemVH ->
                    findItemViewHolderUnder(touchPointF).also { columnVH, itemVH ->
                        swapItemViewHolders(draggingItemVH, itemVH, draggingColumnVH, columnVH)
                    }
                }
            }

            override fun onStartDrag(dragView: View) {
                val (column, item) = findItemViewHolderUnder(touchPointF)
                draggingItem.columnViewHolder = column
                draggingItem.itemViewHolder = item
                itemDragShadow.isVisible = true
                draggingItem.itemViewHolder?.itemView?.alpha = 0F
                scroller.start()
                swapper.start()
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                scroller.stop()
                swapper.stop()
            }

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

    private inline fun initializeListDragShadow() {
        listDragShadow.dragBehavior.addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {

            private val scroller = SyncedRenderer {
                boardView.horizontalScroll(touchPointF)
            }

            private val swapper = SyncedRenderer {
                draggingColumnVH?.also { draggingColumnVH ->
                    findBoardViewHolderUnder(touchPointF)?.also { newVH ->
                        swapColumnViewHolders(draggingColumnVH, newVH)
                    }
                }
            }

            override fun onStartDrag(dragView: View) {
                draggingColumnVH = findBoardViewHolderUnder(touchPointF)
                listDragShadow.isVisible = true
                draggingColumnVH?.itemView?.alpha = 0F
                scroller.start()
                swapper.start()
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                scroller.stop()
                swapper.stop()
            }

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

    private fun findItemViewHolderUnder(point: PointF): DraggingItem {
        var boardVH: BoardColumnViewHolder? = null
        var itemVH: BoardItemViewHolder? = null
        findBoardViewHolderUnder(point)?.also {
            boardVH = it
            findItemViewHolderUnder(it, point)?.also {
                itemVH = it
            }
        }
        return DraggingItem(boardVH, itemVH)
    }

    private inline fun findItemViewHolderUnder(boardVH: BoardColumnViewHolder, point: PointF):
            BoardItemViewHolder? {
        return boardVH.list?.let { boardList ->
            boardList.findChildViewUnderRaw(point.x, point.y)?.let { view ->
                boardList.getChildViewHolder(view) as? BoardItemViewHolder
            }
        }
    }

    private fun forceFindItemViewHolderUnder(point: PointF): Pair<BoardColumnViewHolder?, BoardItemViewHolder?> {
        var boardVH: BoardColumnViewHolder? = null
        var itemVH: BoardItemViewHolder? = null
        findBoardViewHolderUnder(point)?.also {
            boardVH = it
            forceFindItemViewHolderUnder(it, point)?.also {
                itemVH = it
            }
        }
        return Pair(boardVH, itemVH)
    }

    // Forces to find a VH within the vertical bounds unless none even exist
    // TODO: 15-Mar-20 Not yet finished, not optimal and causes freezes because too much work
    private fun forceFindItemViewHolderUnder(boardVH: BoardColumnViewHolder, point: PointF): BoardItemViewHolder? {
        boardVH.list?.let { boardList ->
            if (boardList.isEmpty() || boardList.adapter?.itemCount == 0) return null

            var result: BoardItemViewHolder? = findItemViewHolderUnder(boardVH, point)
            val pointUp = point
            val pointDown = point

            val diff = 4
            val maxAttempts = boardList.globalVisibleRect.height() / 2 * diff
            var attemptNumber = 0

            while (result == null && attemptNumber < maxAttempts) {
                result = boardList.findChildViewUnderRaw(pointUp.x, pointUp.y)?.let { view ->
                    boardList.getChildViewHolder(view) as? BoardItemViewHolder
                }
                if (result != null) return result
                result = boardList.findChildViewUnderRaw(pointDown.x, pointDown.y)?.let { view ->
                    boardList.getChildViewHolder(view) as? BoardItemViewHolder
                }
                if (result != null) return result
                pointUp.y -= 4
                pointDown.y += 4
                attemptNumber++
            }
            logE(result)
            return result
        }
        return null
    }

    private fun findBoardViewHolderUnder(point: PointF): BoardColumnViewHolder? {
        return boardView.findChildViewUnderRaw(point.x, point.y)?.let {
            boardView.getChildViewHolder(it) as? BoardColumnViewHolder
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // return true to tell system that we will handle all touch events in onTouchEvent
        return true
    }

    @SuppressLint("ClickableViewAccessibility") // We are not Accessibility friendly :/
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
                    logE("Animation finished! $now")
                    logE(draggingItem.itemViewHolder?.itemView?.alpha)
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

    public inline fun startDraggingItem(boardItemViewHolder: BoardItemViewHolder) {
        itemDragShadow.updateToMatch(boardItemViewHolder.itemView)
        itemDragShadow.dragBehavior.startDrag()
    }

    public inline fun startDraggingColumn(boardColumnViewHolder: BoardColumnViewHolder) {
        listDragShadow.updateToMatch(boardColumnViewHolder.itemView)
        listDragShadow.dragBehavior.startDrag()
    }

    companion object {
        internal val MAX_POOL_COUNT = 25
        internal val ITEM_VH_POOL = object : RecyclerView.RecycledViewPool() {
            override fun setMaxRecycledViews(viewType: Int, max: Int) =
                    super.setMaxRecycledViews(viewType, MAX_POOL_COUNT)
        }
    }

}

// Represents a ViewHolder Swap which we can use to track if swaps are completed
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

private data class DraggingItem(
        var columnViewHolder: BoardColumnViewHolder? = null,
        var itemViewHolder: BoardItemViewHolder? = null) {

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