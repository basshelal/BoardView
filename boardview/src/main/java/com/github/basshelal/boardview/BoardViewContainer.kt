@file:Suppress("RedundantVisibilityModifier", "NOTHING_TO_INLINE")

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
import com.github.basshelal.boardview.drag.FrameSyncDragListener
import com.github.basshelal.boardview.drag.ObservableDragBehavior.DragState.DRAGGING
import com.github.basshelal.boardview.utils.CalledOnce
import com.github.basshelal.boardview.utils.isAdapterPositionValid
import com.github.basshelal.boardview.utils.isValidAdapterPosition
import com.github.basshelal.boardview.utils.lastPosition
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*
import java.util.Objects

/**
 * The container that will contain a [BoardView] as well as the [DragShadow]s for dragging
 * functionality of [BoardList]s and ItemViews, this is how we do dragging, if you only
 * wants a Board with no dragging functionality use [BoardView]
 */
public class BoardViewContainer
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

    /** The current [DraggingItem] when dragging [itemDragShadow] */
    public val draggingItem = DraggingItem()

    /** The current [BoardColumnViewHolder] when dragging [listDragShadow] */
    public var draggingColumn: BoardColumnViewHolder? = null
        private set

    //region Private variables

    /** The current touch point, updated in [onTouchEvent] */
    private var touchPoint = PointF()

    /**
     * The last requested [ColumnSwap]
     * we use this to ensure we don't request the same swap more than once
     */
    private var requestedColumnSwap: ColumnSwap? = null

    /**
     * The last requested [ItemSwap]
     * we use this to ensure we don't request the same swap more than once
     */
    private var requestedItemSwap: ItemSwap? = null

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
     * Handle touch events and set [touchPoint]
     * Unless user is dragging, all events are forwarded to [boardView]
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchPoint.set(event.rawX, event.rawY)
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
        itemDragShadow.dragBehavior.addDragListenerIfNotExists(object : FrameSyncDragListener() {

            /** User has started drag, initialize everything */
            override fun onStartDrag(dragView: View) {
                super.onStartDrag(dragView)
                val (column, item) = itemUnderTouchPoint
                draggingItem.columnViewHolder = column
                draggingItem.itemViewHolder = item
                itemDragShadow.isVisible = true
                requestedItemSwap = null
            }

            /** Next frame has executed, scroll and swap */
            override fun onNextFrame(frameTimeNanos: Long) {
                boardView.horizontalScroll(touchPoint)
                draggingItem.columnViewHolder?.also { it.list?.verticalScroll(touchPoint) }
                draggingItem.also { draggingColumnVH, draggingItemVH ->
                    val (column, item) = itemUnderTouchPoint

                    column?.also { columnVH ->
                        swapItemViewHolders(draggingItemVH, item, draggingColumnVH, columnVH)
                    }
                }
            }

            /** User has released drag, stop scroll & swap and set up return animation */
            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                frameSynchronizer.stop()
                draggingItem.itemViewHolder?.itemView?.also {
                    itemDragShadow.dragBehavior.returnTo(it)
                }
            }

            /** Animation has ended, drag is finished, finalize everything */
            override fun onEndDrag(dragView: View) {
                super.onEndDrag(dragView)
                itemDragShadow.isVisible = false
                draggingItem.itemViewHolder = null
                draggingItem.columnViewHolder = null
                requestedItemSwap = null
            }
        })
    }

    @CalledOnce
    private inline fun initializeListDragShadow() {
        listDragShadow.dragBehavior.addDragListenerIfNotExists(object : FrameSyncDragListener() {

            /** User has started drag, initialize everything */
            override fun onStartDrag(dragView: View) {
                super.onStartDrag(dragView)
                draggingColumn = boardView.getViewHolderUnder(touchPoint)
                listDragShadow.isVisible = true
                requestedColumnSwap = null
            }

            /** Next frame has executed, scroll and swap */
            override fun onNextFrame(frameTimeNanos: Long) {
                boardView.horizontalScroll(touchPoint)
                draggingColumn?.also { draggingColumnVH ->
                    boardView.getViewHolderUnder(touchPoint)?.also { newVH ->
                        swapColumnViewHolders(draggingColumnVH, newVH)
                    }
                }
            }

            /** User has released drag, stop scroll & swap and set up return animation */
            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                frameSynchronizer.stop()
                draggingItem.columnViewHolder?.itemView?.also {
                    listDragShadow.dragBehavior.returnTo(it)
                }
            }

            /** Animation has ended, drag is finished, finalize everything */
            override fun onEndDrag(dragView: View) {
                super.onEndDrag(dragView)
                listDragShadow.isVisible = false
                draggingColumn = null
                requestedColumnSwap = null
            }
        })
    }

    @CalledOnce
    private inline fun swapColumnViewHolders(draggingColumn: BoardColumnViewHolder,
                                             targetColumn: BoardColumnViewHolder) {
        val targetPosition = targetColumn.adapterPosition
        if (targetColumn != draggingColumn &&
                boardView.itemAnimator?.isRunning != true &&
                draggingColumn.isAdapterPositionValid && targetPosition.isValidAdapterPosition) {
            val swap = ColumnSwap(draggingColumn, targetPosition)
            if (swap != requestedColumnSwap &&
                    adapter?.onMoveColumn(draggingColumn, targetPosition) == true) {
                boardView.boardAdapter?.notifyItemMoved(draggingColumn.adapterPosition, targetPosition)
                boardView.prepareForDrop(draggingColumn, targetColumn)
                listDragShadow.dragBehavior.returnTo(targetColumn.itemView)
            }
            requestedColumnSwap = swap
        }
    }

    @CalledOnce
    private inline fun swapItemViewHolders(draggingItemVH: BoardItemViewHolder,
                                           targetItemVH: BoardItemViewHolder?,
                                           draggingColumnVH: BoardColumnViewHolder,
                                           targetColumnVH: BoardColumnViewHolder) {

        val draggingItemVHPosition = draggingItemVH.adapterPosition
        val targetItemVHPosition = targetItemVH?.adapterPosition ?: -1
        val draggingColumnVHPosition = draggingColumnVH.adapterPosition
        val targetColumnVHPosition = targetColumnVH.adapterPosition

        if (draggingColumnVHPosition == targetColumnVHPosition) {
            if (draggingItemVHPosition == targetItemVHPosition) return
            else swapItemVHsSameList(draggingItemVH, targetItemVH, draggingColumnVH)
        } else swapItemVHsDiffList(draggingItemVH, targetItemVH, draggingColumnVH, targetColumnVH)
    }

    @CalledOnce
    private inline fun swapItemVHsSameList(draggingItemVH: BoardItemViewHolder,
                                           targetItemVH: BoardItemViewHolder?,
                                           columnVH: BoardColumnViewHolder) {
        val targetPosition = targetItemVH?.adapterPosition
                ?: columnVH.boardListAdapter?.lastPosition
                ?: return
        val swap = ItemSwap(draggingItemVH, targetPosition, columnVH, columnVH)
        if (boardView.itemAnimator?.isRunning != true &&
                columnVH.list?.itemAnimator?.isRunning != true &&
                targetPosition.isValidAdapterPosition) {
            if (swap != requestedItemSwap &&
                    adapter?.onMoveItem(draggingItemVH, targetPosition, columnVH, columnVH) == true) {
                targetItemVH?.itemView?.also { itemDragShadow.dragBehavior.returnTo(it) }
                columnVH.boardListAdapter?.notifyItemMoved(draggingItemVH.adapterPosition, targetPosition)
                columnVH.list?.prepareForDrop(draggingItemVH, targetItemVH)
            }
            requestedItemSwap = swap
        }
    }

    @CalledOnce
    private inline fun swapItemVHsDiffList(draggingItemVH: BoardItemViewHolder,
                                           targetItemVH: BoardItemViewHolder?,
                                           draggingColumnVH: BoardColumnViewHolder,
                                           targetColumnVH: BoardColumnViewHolder) {

        val targetItemVHPosition = targetItemVH?.adapterPosition
                ?: targetColumnVH.boardListAdapter?.lastPosition?.plus(1)
                ?: return
        val draggingItemVHPosition = draggingItemVH.adapterPosition

        val swap = ItemSwap(draggingItemVH, targetItemVHPosition, draggingColumnVH, targetColumnVH)
        if (boardView.itemAnimator?.isRunning != true &&
                draggingColumnVH.list?.itemAnimator?.isRunning != true &&
                targetColumnVH.list?.itemAnimator?.isRunning != true &&
                draggingItemVH.isAdapterPositionValid &&
                targetItemVHPosition.isValidAdapterPosition) {
            if (swap != requestedItemSwap &&
                    adapter?.onMoveItem(draggingItemVH, targetItemVHPosition, draggingColumnVH, targetColumnVH) == true) {
                targetItemVH?.itemView?.also { itemDragShadow.dragBehavior.returnTo(it) }
                draggingColumnVH.boardListAdapter?.notifyItemRemoved(draggingItemVHPosition)
                targetColumnVH.boardListAdapter?.notifyItemInserted(targetItemVHPosition)
                targetColumnVH.list?.prepareForDrop(draggingItemVH, targetItemVH)
                targetColumnVH.list?.boardListItemAnimator?.draggingItemInserted {
                    draggingItem.itemViewHolder = it as? BoardItemViewHolder
                    draggingItem.columnViewHolder = targetColumnVH
                }
            }
            requestedItemSwap = swap
        }
    }

    private inline val itemUnderTouchPoint: DraggingItem
        get() {
            var boardVH: BoardColumnViewHolder? = null
            var itemVH: BoardItemViewHolder? = null
            boardView.getViewHolderUnder(touchPoint)?.also {
                boardVH = it
                it.list?.getViewHolderUnder(touchPoint)?.also { itemVH = it }
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

    /** Pair of [BoardColumnViewHolder] and [BoardItemViewHolder] used in [draggingItem] */
    public class DraggingItem(columnViewHolder: BoardColumnViewHolder? = null,
                              itemViewHolder: BoardItemViewHolder? = null) {

        /** Callback that callers can use to be notified when the value of [columnViewHolder] has changed */
        var onChangedColumnViewHolder: (oldValue: BoardColumnViewHolder?, newValue: BoardColumnViewHolder?) -> Unit = { _, _ -> }

        /** Callback that callers can use to be notified when the value of [itemViewHolder] has changed */
        var onChangedItemViewHolder: (oldValue: BoardItemViewHolder?, newValue: BoardItemViewHolder?) -> Unit = { _, _ -> }

        var columnViewHolder: BoardColumnViewHolder? = columnViewHolder
            internal set(value) {
                onChangedColumnViewHolder(field, value)
                field = value
            }
        var itemViewHolder: BoardItemViewHolder? = itemViewHolder
            internal set(value) {
                onChangedItemViewHolder(field, value)
                field = value
            }

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

        inline operator fun component1(): BoardColumnViewHolder? = columnViewHolder
        inline operator fun component2(): BoardItemViewHolder? = itemViewHolder
        override fun hashCode(): Int = Objects.hash(columnViewHolder, itemViewHolder)

        override fun equals(other: Any?): Boolean = other is DraggingItem &&
                other.columnViewHolder == this.columnViewHolder &&
                other.itemViewHolder == this.itemViewHolder

        override fun toString(): String = "DraggingItem(" +
                "columnViewHolder= $columnViewHolder, " +
                "itemViewHolder= $itemViewHolder)"
    }

    private data class ColumnSwap(val draggingColumn: BoardColumnViewHolder, val targetPosition: Int)

    private data class ItemSwap(val draggingItem: BoardItemViewHolder, val targetPosition: Int,
                                val draggingColumn: BoardColumnViewHolder, val targetColumn: BoardColumnViewHolder)
}