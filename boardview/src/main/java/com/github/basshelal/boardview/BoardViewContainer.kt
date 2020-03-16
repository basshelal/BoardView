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
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.github.basshelal.boardview.drag.DragShadow
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import com.github.basshelal.boardview.drag.ObservableDragBehavior.DragState.DRAGGING
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*
import java.util.concurrent.TimeUnit
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
            boardView.adapter = value?.getBoardViewAdapter()
            value?.boardViewContainer = this
        }

    public inline val boardView: BoardView get() = this._boardView
    public inline val itemDragShadow: DragShadow get() = this.item_dragShadow
    public inline val listDragShadow: DragShadow get() = this.list_dragShadow

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
    private val columnVHSwaps = HashMap<ViewHolderSwap<BoardColumnViewHolder>, Boolean>()
    private val itemVHSwaps = HashMap<ViewHolderSwap<BoardItemViewHolder>, Boolean>()

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        itemDragShadow()
        listDragShadow()
    }

    private inline fun itemDragShadow() {
        itemDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

            val onNext = {
                if (draggingItemVH != null && draggingItemVHColumn != null) {
                    boardView.horizontalScroll(touchPointF)
                    draggingItemVHColumn?.list?.also { it.verticalScroll(touchPointF) }

                    draggingItemVHColumn?.also { draggingColumn ->
                        draggingItemVH?.also { draggingVH ->
                            findItemViewHolderUnder(touchPointF).also { (column, itemVH) ->
                                if (column != null && itemVH != null)
                                    swapItemViewHolders(draggingVH, itemVH, draggingColumn, column)
                            }
                        }
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                val (column, item) = findItemViewHolderUnder(touchPointF)
                draggingItemVHColumn = column
                draggingItemVH = item
                itemDragShadow.isVisible = true
                draggingItemVH?.itemView?.alpha = 1F
                disposable = Observable.interval(updateRatePerMilli.L, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.computation())
                        .subscribe { onNext() }
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                draggingItemVH?.itemView?.also { itemDragShadow.dragBehavior.returnTo(it) }
                disposable?.dispose()
            }

            override fun onEndDrag(dragView: View) {
                draggingItemVH?.itemView?.alpha = 1F
                itemDragShadow.isVisible = false
                draggingItemVH = null
            }
        }
    }

    private inline fun listDragShadow() {
        listDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

            val onNext = {
                if (draggingColumnVH != null) {
                    boardView.horizontalScroll(touchPointF)
                    findBoardViewHolderUnder(touchPointF)?.also { newVH ->
                        draggingColumnVH?.also { draggingVH ->
                            swapColumnViewHolders(draggingVH, newVH)
                        }
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                draggingColumnVH = findBoardViewHolderUnder(touchPointF)
                listDragShadow.isVisible = true
                draggingColumnVH?.itemView?.alpha = 0F
                disposable = Observable.interval(updateRatePerMilli.L, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.computation())
                        .subscribe { onNext() }
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                draggingColumnVH?.itemView?.also { listDragShadow.dragBehavior.returnTo(it) }
                disposable?.dispose()
            }

            override fun onEndDrag(dragView: View) {
                draggingColumnVH?.itemView?.alpha = 1F
                listDragShadow.isVisible = false
                draggingColumnVH = null
            }
        }
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

    fun swapItemViewHolders(oldItemVH: BoardItemViewHolder, newItemVH: BoardItemViewHolder,
                            oldColumnVH: BoardColumnViewHolder, newColumnVH: BoardColumnViewHolder) {
        if (oldItemVH != newItemVH) {
            val swap = ViewHolderSwap(oldItemVH, newItemVH)
            if (!itemVHSwaps.containsKey(swap)) itemVHSwaps[swap] = false
            if (itemVHSwaps[swap] == false &&
                    boardView.itemAnimator?.isRunning == false &&
                    oldColumnVH.list?.itemAnimator?.isRunning == false &&
                    newColumnVH.list?.itemAnimator?.isRunning == false) {
                if (adapter?.onSwapItemViewHolders(oldItemVH, newItemVH, oldColumnVH, newColumnVH) == true) {
                    notifyItemViewHoldersSwapped(oldItemVH, newItemVH, oldColumnVH, newColumnVH)
                }
                itemVHSwaps[swap] = true
                itemVHSwaps.remove(swap)
            }
        }
    }

    fun notifyItemViewHoldersSwapped(oldItemVH: BoardItemViewHolder, newItemVH: BoardItemViewHolder,
                                     oldColumnVH: BoardColumnViewHolder, newColumnVH: BoardColumnViewHolder) {
        val fromItem = oldItemVH.adapterPosition
        val toItem = newItemVH.adapterPosition
        val fromColumn = oldColumnVH.adapterPosition
        val toColumn = newColumnVH.adapterPosition

        if (fromItem != NO_POSITION && toItem != NO_POSITION &&
                fromColumn != NO_POSITION && toColumn != NO_POSITION) {
            logE("Swapping item $fromItem from column $fromColumn " +
                    "to column $toColumn at item $toItem")

            when {
                // They are in the same list
                fromColumn == toColumn -> {
                    if (fromItem != toItem) {
                        oldColumnVH.boardListAdapter?.notifyItemMoved(
                                fromItem, toItem
                        )
                    }
                }
                // They are in different lists
                fromColumn != toColumn -> {
                    oldColumnVH.boardListAdapter?.notifyItemRemoved(fromItem)
                    newColumnVH.boardListAdapter?.notifyItemInserted(toItem)
                }
            }
        }
    }

    fun swapColumnViewHolders(oldVH: BoardColumnViewHolder, newVH: BoardColumnViewHolder) {
        if (newVH != oldVH) {
            val swap = ViewHolderSwap(oldVH, newVH)
            if (!columnVHSwaps.containsKey(swap)) columnVHSwaps[swap] = false
            if (columnVHSwaps[swap] == false &&
                    boardView.itemAnimator?.isRunning == false) {
                if (adapter?.onSwapBoardViewHolders(oldVH, newVH) == true) {
                    notifyColumnViewHoldersSwapped(oldVH, newVH)
                    listDragShadow.dragBehavior.returnTo(newVH.itemView)
                }
                columnVHSwaps[swap] = true
                columnVHSwaps.remove(swap)
            }
        }
    }

    fun notifyColumnViewHoldersSwapped(oldVH: BoardColumnViewHolder, newVH: BoardColumnViewHolder) {
        val from = oldVH.adapterPosition
        val to = newVH.adapterPosition
        val boardAdapter = boardView.adapter as? BoardAdapter

        if (from != to && from != NO_POSITION && to != NO_POSITION) {
            boardAdapter?.apply {
                logE("Swapping from $from to $to")
                // TODO: 14-Mar-20 ItemAnimator fucks up when one of from or to is the VH at
                //  layout position 0, it animates things in a stupid way such that the RV ends
                //  up kind of scrolling across when this happens!!! Either hack a fix or use
                //  another Item Animator
                //  But there is a small chance that this is on me!

                // TODO: 15-Mar-20 Why the actual fuck does this not work??
                //  notifySwapped kinda works but it's extremely inefficient!

                notifyItemMoved(from, to)
            }
        }
    }

    fun getBoardColumnID(holder: BoardColumnViewHolder): Long {
        return boardView.boardAdapter?.getItemId(holder.adapterPosition) ?: RecyclerView.NO_ID
    }

    // TODO: 15-Mar-20 Write code that will save and restore state correctly
    //  we mainly need to save Layout States but possibly more as well

    public inline fun startDraggingItem(vh: BoardItemViewHolder) {
        itemDragShadow.updateToMatch(vh.itemView)
        itemDragShadow.dragBehavior.startDrag()
    }

    public inline fun startDraggingColumn(vh: BoardColumnViewHolder) {
        listDragShadow.updateToMatch(vh.itemView)
        listDragShadow.dragBehavior.startDrag()
    }

    companion object {
        val MAX_POOL_COUNT = 25
        val ITEM_VH_POOL = object : RecyclerView.RecycledViewPool() {
            override fun setMaxRecycledViews(viewType: Int, max: Int) =
                    super.setMaxRecycledViews(viewType, MAX_POOL_COUNT)
        }
    }

}

// Represents a ViewHolder Swap which we can use to track if swaps are completed
private data class ViewHolderSwap<VH : BaseViewHolder>(val old: VH, val new: VH)