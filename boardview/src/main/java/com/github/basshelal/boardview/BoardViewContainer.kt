@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.contains
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.github.basshelal.boardview.drag.DragShadow
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.container_boardviewcontainer.view.*
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.roundToInt

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

    var touchPointF = PointF()

    var draggingItemVH: BoardViewItemVH? = null

    // the column which the dragging Item belongs to, this will change when the item has been
    // dragged across to a new column
    var draggingItemVHColumn: BoardViewColumnVH? = null

    var draggingColumnVH: BoardViewColumnVH? = null

    // We update observers based on the screen refresh rate because animations are not able to
    // keep up with a faster update rate, this is only a problem with scrolling since we don't
    // want scrolling to be based on refresh rate (higher refresh rate screens scroll faster like
    // Fallout 76, country roads take me HOOOOOOOOME!) so we offset this by ensuring that the
    // scroll rate is constant no matter how high the refresh rate (1 px per 1 ms)
    val updateRatePerMilli = floor(millisPerFrame)
    val scrollRatePerMilli = 1F

    private val columnVHSwaps = HashMap<ViewHolderSwap<BoardViewColumnVH>, Boolean>()
    private val itemVHSwaps = HashMap<ViewHolderSwap<BoardViewItemVH>, Boolean>()

    private val interpolator = LogarithmicInterpolator()

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        itemDragShadow()
        listDragShadow()
    }

    private inline fun itemDragShadow() {
        itemDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

            val onNext = {
                if (draggingItemVH != null && draggingItemVHColumn != null) {
                    horizontalScroll(touchPointF)
                    draggingItemVHColumn?.list?.also { verticalScroll(touchPointF, it) }

                    draggingItemVH?.also { draggingVH ->
                        // swapItemViewHolders(draggingVH, newItemVH)
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                val (column, item) = findItemViewHolderUnderRaw(touchPointF.x, touchPointF.y)
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
                    horizontalScroll(touchPointF)
                    findBoardViewHolderUnderRaw(touchPointF.x, touchPointF.y)?.also { newVH ->
                        draggingColumnVH?.also { draggingVH ->
                            swapBoardViewHoldersView(draggingVH, newVH)
                        }
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                draggingColumnVH = findBoardViewHolderUnderRaw(touchPointF.x, touchPointF.y)
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

    // We should take into account the white parts (empty areas) when finding the ViewHolder
    // under a TouchPoint, especially for ItemViewHolders, since if the caller has set large
    // margins we still want things to work properly
    // This can be done using a simple find first algorithm

    inline fun findItemViewHolderUnderRaw(rawX: Float, rawY: Float)
            : Pair<BoardViewColumnVH?, BoardViewItemVH?> {
        var boardVH: BoardViewColumnVH? = null
        var itemVH: BoardViewItemVH? = null
        findBoardViewHolderUnderRaw(rawX, rawY)?.also {
            boardVH = it
            findItemViewHolderUnderRaw(it, rawX, rawY)?.also {
                itemVH = it
            }
        }
        return Pair(boardVH, itemVH)
    }

    inline fun findItemViewHolderUnderRaw(boardVH: BoardViewColumnVH, rawX: Float, rawY: Float): BoardViewItemVH? {
        return boardVH.list?.let { boardList ->
            boardList.findChildViewUnderRaw(rawX, rawY)?.let { view ->
                boardList.getChildViewHolder(view) as? BoardViewItemVH
            }
        }
    }

    inline fun findBoardViewHolderUnderRaw(rawX: Float, rawY: Float): BoardViewColumnVH? {
        return boardView.findChildViewUnderRaw(rawX, rawY)?.let {
            boardView.getChildViewHolder(it) as? BoardViewColumnVH
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // return true here to tell system that I will handle all subsequent touch events
        // they will thus not immediately go down to my children in that case
        return true
    }

    // return false to tell system to stop sending events
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchPointF.set(event.rawX, event.rawY)
        val result: Boolean
        if (itemDragShadow.dragBehavior.dragState == ObservableDragBehavior.DragState.DRAGGING) {
            itemDragShadow.dragBehavior.onTouchEvent(event)
            result = true
        } else if (listDragShadow.dragBehavior.dragState == ObservableDragBehavior.DragState.DRAGGING) {
            listDragShadow.dragBehavior.onTouchEvent(event)
            result = true
        } else {
            result = boardView.dispatchTouchEvent(event)
        }
        return result
    }

    inline fun startDraggingItem(vh: BoardViewItemVH) {
        itemDragShadow.updateToMatch(vh.itemView)
        itemDragShadow.dragBehavior.startDrag()
    }

    inline fun startDraggingList(vh: BoardViewColumnVH) {
        listDragShadow.updateToMatch(vh.itemView)
        listDragShadow.dragBehavior.startDrag()
    }

    fun swapItemViewHolders(old: BoardViewItemVH, new: BoardViewItemVH) {
        // TODO: 13-Mar-20 Do stuff when they're both in the same list
        // TODO: 13-Mar-20 Do more complicated stuff when they're both in different lists
        val from = old.adapterPosition
        val to = new.adapterPosition

        if (from != to && from != NO_POSITION && to != NO_POSITION) {
            logE("Swapping from $from to $to")
        }
    }

    fun swapBoardViewHoldersView(oldVH: BoardViewColumnVH, newVH: BoardViewColumnVH) {
        if (newVH != oldVH) {
            val swap = ViewHolderSwap(oldVH, newVH)
            if (!columnVHSwaps.containsKey(swap)) columnVHSwaps[swap] = false
            if (columnVHSwaps[swap] == false
                    && boardView.itemAnimator?.isRunning == false) {
                adapter?.onSwapBoardViewHolders(oldVH, newVH)
                swapBoardViewHoldersAdapter(oldVH, newVH)
                columnVHSwaps[swap] = true
                columnVHSwaps.remove(swap)
            }
        }
    }

    fun swapBoardViewHoldersAdapter(old: BoardViewColumnVH, new: BoardViewColumnVH) {
        val from = old.adapterPosition
        val to = new.adapterPosition
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

    fun horizontalScroll(touchPoint: PointF) {
        val maxScrollBy = (updateRatePerMilli * 2F).roundToInt()
        val width = boardView.globalVisibleRectF.width() / 5F
        val leftBounds = boardView.globalVisibleRectF.also {
            it.right = width
            it.left = 0F
            it.top = 0F
            it.bottom = realScreenHeight.F
        }
        val rightBounds = boardView.globalVisibleRectF.also {
            it.left = it.width() - width
            it.right = realScreenWidth.F
            it.top = 0F
            it.bottom = realScreenHeight.F
        }
        val leftMost = boardView.globalVisibleRectF.left
        val rightMost = boardView.globalVisibleRectF.right
        var scrollBy = 0
        when {
            leftBounds.contains(touchPoint) -> {
                val mult = interpolator[
                        1F - (touchPoint.x - leftMost) / (leftBounds.right - leftMost)]
                scrollBy = -(maxScrollBy * mult).roundToInt()
            }
            rightBounds.contains(touchPoint) -> {
                val mult = interpolator[
                        (touchPoint.x - rightBounds.left) / (rightMost - rightBounds.left)]
                scrollBy = (maxScrollBy * mult).roundToInt()
            }
        }
        boardView.scrollBy(scrollBy, 0)
    }

    fun verticalScroll(touchPoint: PointF, boardList: BoardList) {
        val maxScrollBy = (updateRatePerMilli * 2F).roundToInt()
        val height = boardList.globalVisibleRectF.height() / 8F
        val topBounds = boardList.globalVisibleRectF.also {
            it.bottom = it.top + height
            it.top = 0F
        }
        val bottomBounds = boardList.globalVisibleRectF.also {
            it.top = it.height() - height
            it.bottom = realScreenHeight.F
        }
        val topMost = boardList.globalVisibleRectF.top
        val bottomMost = boardList.globalVisibleRectF.bottom
        var scrollBy = 0
        when {
            topBounds.contains(touchPoint) -> {
                val mult = interpolator[
                        1F - (touchPoint.y - topMost) / (topBounds.bottom - topMost)]
                scrollBy = -(maxScrollBy * mult).roundToInt()
            }
            bottomBounds.contains(touchPoint) -> {
                val mult = interpolator[
                        (touchPoint.y - bottomBounds.top) / (bottomMost - bottomBounds.top)]
                scrollBy = (maxScrollBy * mult).roundToInt()
            }
        }
        logE("$now $scrollBy")
        boardList.scrollBy(0, scrollBy)
    }

    fun getBoardColumnID(holder: BoardViewColumnVH): Long {
        return boardView.boardAdapter?.getItemId(holder.adapterPosition) ?: RecyclerView.NO_ID
    }

    inline fun doAfterFinishAnimation(crossinline block: (BoardView) -> Unit) {
        boardView.itemAnimator?.isRunning { block(boardView) }
    }
}

// Represents a ViewHolder Swap which we can use to track if swaps are completed
private data class ViewHolderSwap<VH : BaseViewHolder>(val old: VH, val new: VH)