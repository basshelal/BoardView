@file:Suppress("NOTHING_TO_INLINE")

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

    var adapter: BoardContainerAdapter? = null
        set(value) {
            field = value
            boardView.adapter = value?.getBoardViewAdapter()
            value?.boardViewContainer = this
        }

    inline val boardView: BoardView get() = this._boardView
    inline val itemDragShadow: DragShadow get() = this.item_dragShadow
    inline val listDragShadow: DragShadow get() = this.list_dragShadow

    private var touchPointF = PointF()

    // the column which the dragging Item belongs to, this will change when the item has been
    // dragged across to a new column
    private var draggingItemVHColumn: BoardViewColumnVH? = null
    private var draggingItemVH: BoardViewItemVH? = null

    private var draggingColumnVH: BoardViewColumnVH? = null

    // We update observers based on the screen refresh rate because animations are not able to
    // keep up with a faster update rate, this is only a problem with scrolling since we don't
    // want scrolling to be based on refresh rate (higher refresh rate screens scroll faster like
    // Fallout 76, country roads take me HOOOOOOOOME!) so we offset this by ensuring that the
    // scroll rate is constant no matter how high the refresh rate (1 px per 1 ms)
    private val updateRatePerMilli = floor(millisPerFrame)

    private val columnVHSwaps = HashMap<ViewHolderSwap<BoardViewColumnVH>, Boolean>()
    private val itemVHSwaps = HashMap<ViewHolderSwap<BoardViewItemVH>, Boolean>()

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
                            findItemViewHolderUnderRaw(touchPointF).also { (column, itemVH) ->
                                if (column != null && itemVH != null)
                                    swapItemViewHoldersView(draggingVH, itemVH, draggingColumn, column)
                            }
                        }
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                val (column, item) = findItemViewHolderUnderRaw(touchPointF)
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
                    findBoardViewHolderUnderRaw(touchPointF)?.also { newVH ->
                        draggingColumnVH?.also { draggingVH ->
                            swapBoardViewHoldersView(draggingVH, newVH)
                        }
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                draggingColumnVH = findBoardViewHolderUnderRaw(touchPointF)
                listDragShadow.isVisible = true
                draggingColumnVH?.itemView?.alpha = 1F
                disposable = Observable.interval(updateRatePerMilli.L, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.computation())
                        .subscribe { onNext() }
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                disposable?.dispose()
            }

            override fun onEndDrag(dragView: View) {
                draggingColumnVH?.itemView?.alpha = 1F
                listDragShadow.isVisible = false
                draggingColumnVH = null
            }
        }
    }

    // TODO: 15-Mar-20 We should take into account the white parts (empty areas) when finding
    //  the ViewHolder under a TouchPoint, especially for ItemViewHolders, since if the caller
    //  has set large margins we still want things to work properly
    //  This can be done using a simple find first algorithm

    private fun findItemViewHolderUnderRaw(point: PointF)
            : Pair<BoardViewColumnVH?, BoardViewItemVH?> {
        var boardVH: BoardViewColumnVH? = null
        var itemVH: BoardViewItemVH? = null
        findBoardViewHolderUnderRaw(point)?.also {
            boardVH = it
            findItemViewHolderUnderRaw(it, point)?.also {
                itemVH = it
            }
        }
        return Pair(boardVH, itemVH)
    }

    private fun findItemViewHolderUnderRaw(boardVH: BoardViewColumnVH, point: PointF): BoardViewItemVH? {
        return boardVH.list?.let { boardList ->
            boardList.findChildViewUnderRaw(point.x, point.y)?.let { view ->
                boardList.getChildViewHolder(view) as? BoardViewItemVH
            }
        }
    }

    private fun findBoardViewHolderUnderRaw(point: PointF): BoardViewColumnVH? {
        return boardView.findChildViewUnderRaw(point.x, point.y)?.let {
            boardView.getChildViewHolder(it) as? BoardViewColumnVH
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // return true here to tell system that I will handle all subsequent touch events
        // they will thus not immediately go down to my children in that case
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

    inline fun startDraggingItem(vh: BoardViewItemVH) {
        itemDragShadow.updateToMatch(vh.itemView)
        itemDragShadow.dragBehavior.startDrag()
    }

    inline fun startDraggingList(vh: BoardViewColumnVH) {
        listDragShadow.updateToMatch(vh.itemView)
        listDragShadow.dragBehavior.startDrag()
    }

    fun swapItemViewHoldersView(oldItemVH: BoardViewItemVH, newItemVH: BoardViewItemVH,
                                oldColumnVH: BoardViewColumnVH, newColumnVH: BoardViewColumnVH) {
        if (oldItemVH != newItemVH) {
            val swap = ViewHolderSwap(oldItemVH, newItemVH)
            if (!itemVHSwaps.containsKey(swap)) itemVHSwaps[swap] = false
            if (itemVHSwaps[swap] == false &&
                    boardView.itemAnimator?.isRunning == false &&
                    oldColumnVH.list?.itemAnimator?.isRunning == false &&
                    newColumnVH.list?.itemAnimator?.isRunning == false) {
                swapItemViewHoldersAdapter(oldItemVH, newItemVH, oldColumnVH, newColumnVH)
                itemVHSwaps[swap] = true
                itemVHSwaps.remove(swap)
            }
        }
    }

    fun swapItemViewHoldersAdapter(oldItemVH: BoardViewItemVH, newItemVH: BoardViewItemVH,
                                   oldColumnVH: BoardViewColumnVH, newColumnVH: BoardViewColumnVH) {
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

    fun swapBoardViewHoldersView(oldVH: BoardViewColumnVH, newVH: BoardViewColumnVH) {
        if (newVH != oldVH) {
            val swap = ViewHolderSwap(oldVH, newVH)
            if (!columnVHSwaps.containsKey(swap)) columnVHSwaps[swap] = false
            if (columnVHSwaps[swap] == false &&
                    boardView.itemAnimator?.isRunning == false) {
                adapter?.onSwapBoardViewHolders(oldVH, newVH)
                swapBoardViewHoldersAdapter(oldVH, newVH)
                columnVHSwaps[swap] = true
                columnVHSwaps.remove(swap)
            }
        }
    }

    fun swapBoardViewHoldersAdapter(oldVH: BoardViewColumnVH, newVH: BoardViewColumnVH) {
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
                notifyItemMoved(from, to)
            }
        }
    }

    fun getBoardColumnID(holder: BoardViewColumnVH): Long {
        return boardView.boardAdapter?.getItemId(holder.adapterPosition) ?: RecyclerView.NO_ID
    }

    // TODO: 15-Mar-20 Write code that will save and restore state correctly
    //  we mainly need to save Layout States but possibly more as well

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