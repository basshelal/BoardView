@file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.get
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.github.basshelal.boardview.drag.DragShadow
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import com.github.basshelal.boardview.drag.ObservableDragBehavior.DragState.DRAGGING
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
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

    // the column which the dragging Item belongs to, this will change when the item has been
    // dragged across to a new column
    private var draggingItemVHColumn: BoardColumnViewHolder? = null
    private var draggingItemVH: BoardItemViewHolder? = null

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

            private var timer: Timer? = null

            override fun onStartDrag(dragView: View) {
                val (column, item) = findItemViewHolderUnder(touchPointF)
                draggingItemVHColumn = column
                draggingItemVH = item
                itemDragShadow.isVisible = true
                draggingItemVH?.itemView?.alpha = 0F
                timer = fixedRateTimer(period = updateRatePerMilli.L) {
                    post {
                        draggingItemVHColumn?.also { draggingItemVHColumn ->
                            draggingItemVH?.also { draggingItemVH ->
                                boardView.horizontalScroll(touchPointF)
                                draggingItemVHColumn.list?.verticalScroll(touchPointF)

                                //  logE("Item Pos: ${draggingItemVH.adapterPosition}")
                                //  logE("Column Pos: ${draggingItemVHColumn.adapterPosition}")
                                // TODO: 17-May-20 Dragging shit isn't changing automatically :/

                                findItemViewHolderUnder(touchPointF).also { (column, itemVH) ->
                                    if (column != null && itemVH != null)
                                        swapItemViewHolders(draggingItemVH, itemVH, draggingItemVHColumn, column)
                                }
                            }
                        }
                    }
                }
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                draggingItemVH?.itemView?.also { itemDragShadow.dragBehavior.returnTo(it) }
                timer?.cancel()
            }

            override fun onEndDrag(dragView: View) {
                draggingItemVH?.itemView?.alpha = 1F
                itemDragShadow.isVisible = false
                draggingItemVH = null
                timer = null
                pendingItemVHSwaps.clear()
            }
        })
    }

    private inline fun initializeListDragShadow() {
        listDragShadow.dragBehavior.addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {

            private var timer: Timer? = null

            override fun onStartDrag(dragView: View) {
                draggingColumnVH = findBoardViewHolderUnder(touchPointF)
                listDragShadow.isVisible = true
                draggingColumnVH?.itemView?.alpha = 0F
                timer = fixedRateTimer(period = updateRatePerMilli.L) {
                    post {
                        draggingColumnVH?.also { draggingColumnVH ->
                            boardView.horizontalScroll(touchPointF)
                            findBoardViewHolderUnder(touchPointF)?.also { newVH ->
                                swapColumnViewHolders(draggingColumnVH, newVH)
                            }
                        }
                    }
                }
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                draggingColumnVH?.itemView?.also { listDragShadow.dragBehavior.returnTo(it) }
                timer?.cancel()
            }

            override fun onEndDrag(dragView: View) {
                draggingColumnVH?.itemView?.alpha = 1F
                listDragShadow.isVisible = false
                draggingColumnVH = null
                timer = null
                pendingColumnVHSwaps.clear()
            }
        })
    }

    private fun findItemViewHolderUnder(point: PointF): Pair<BoardColumnViewHolder?, BoardItemViewHolder?> {
        var boardVH: BoardColumnViewHolder? = null
        var itemVH: BoardItemViewHolder? = null
        findBoardViewHolderUnder(point)?.also {
            boardVH = it
            findItemViewHolderUnder(it, point)?.also {
                itemVH = it
            }
        }
        return Pair(boardVH, itemVH)
    }

    private fun findItemViewHolderUnder(boardVH: BoardColumnViewHolder, point: PointF): BoardItemViewHolder? {
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

    private fun swapItemViewHolders(oldItemVH: BoardItemViewHolder, newItemVH: BoardItemViewHolder,
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

    private fun notifyItemViewHoldersSwapped(oldItemVH: BoardItemViewHolder, newItemVH: BoardItemViewHolder,
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
                    oldColumnVH.boardListAdapter?.notifyItemMoved(fromItem, toItem)
            }
            // They are in different lists
            fromColumn != toColumn -> {
                oldColumnVH.boardListAdapter?.notifyItemRemoved(fromItem)
                newColumnVH.boardListAdapter?.notifyItemInserted(toItem)
            }
        }

    }

    fun swapColumnViewHolders(oldVH: BoardColumnViewHolder, newVH: BoardColumnViewHolder) {
        // VHs must be valid, different and Board is not animating anything
        if (newVH != oldVH && boardView.itemAnimator?.isRunning != true &&
                oldVH.isAdapterPositionValid && newVH.isAdapterPositionValid) {
            val swap = ViewHolderSwap(oldVH, newVH)
            pendingColumnVHSwaps.add(swap) // Will only be added if it doesn't exist already
            if (swap in pendingColumnVHSwaps && !swap.hasSwapped) { // VHSwap has been queued but not executed, perform swap!
                if (adapter?.onSwapBoardViewHolders(oldVH, newVH) == true) { // Caller has told us the swap is successful, let's animate the swap!
                    notifyColumnViewHoldersSwapped(oldVH, newVH)
                    listDragShadow.dragBehavior.returnTo(newVH.itemView)
                }
                // Remove the swap in any case, its life is over
                swap.hasSwapped = true
                pendingColumnVHSwaps.remove(swap)
            }
        }
    }

    private inline fun notifyColumnViewHoldersSwapped(oldVH: BoardColumnViewHolder, newVH: BoardColumnViewHolder) {
        // From & To are guarenteed to be valid and different!
        val from = oldVH.adapterPosition
        val to = newVH.adapterPosition

        /* Weird shit happens whenever we do a swap with an item at layout position 0,
         * This is because of how LinearLayoutManager works, it ends up scrolling for us even
         * though we never told it to, see more here
         * https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
         * So we solve this by forcing it back where it was, essentially cancelling the
         * scroll it did
         */
        if (oldVH.layoutPosition == 0 || newVH.layoutPosition == 0 ||
                boardView[0] == oldVH.itemView || boardView[0] == newVH.itemView) {
            boardView.layoutManager?.also { boardViewLayoutManager ->
                boardViewLayoutManager.findFirstVisibleItemPosition()
                        .takeIf { it.isValidAdapterPosition }?.also { firstPosition ->
                            boardView.findViewHolderForAdapterPosition(firstPosition)?.itemView?.also { firstView ->
                                when (boardView.boardLayoutDirection) {
                                    View.LAYOUT_DIRECTION_LTR -> {
                                        val offset = boardViewLayoutManager.getDecoratedLeft(firstView) -
                                                boardViewLayoutManager.getLeftDecorationWidth(firstView)
                                        val margin = (firstView.layoutParams as? MarginLayoutParams)?.leftMargin
                                                ?: 0
                                        boardView.boardAdapter?.notifyItemMoved(from, to)
                                        boardViewLayoutManager.scrollToPositionWithOffset(firstPosition, offset)
                                    }
                                    // TODO: 26-Mar-20 Figure out RTL and margins but
                                    //  otherwise everything else is mostly correct
                                    View.LAYOUT_DIRECTION_RTL -> {
                                        val offset = boardViewLayoutManager.getDecoratedRight(firstView) -
                                                boardViewLayoutManager.getRightDecorationWidth(firstView)
                                        val margin = (firstView.layoutParams as? MarginLayoutParams)?.rightMargin
                                                ?: 0
                                        boardView.boardAdapter?.notifyItemMoved(from, to)
                                        boardViewLayoutManager.scrollToPositionWithOffset(firstPosition, offset)
                                    }
                                }
                            }
                        }
            }
        } else boardView.boardAdapter?.notifyItemMoved(from, to)
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
