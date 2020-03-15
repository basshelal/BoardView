@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.contains
import androidx.core.view.isVisible
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
    var draggingColumnVH: BoardViewColumnVH? = null

    val updateRatePerMilli = floor(millisPerFrame)
    val scrollRatePerMilli = 1F

    private val boardVHSwaps = HashMap<ViewHolderSwap<BoardViewColumnVH>, Boolean>()
    private val itemVHSwaps = HashMap<ViewHolderSwap<BoardViewItemVH>, Boolean>()

    private val interpolator = AccelerateDecelerateInterpolator()

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        itemDragShadow()
        listDragShadow()
    }

    private inline fun itemDragShadow() {
        itemDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

            val onNext = {
                if (draggingItemVH != null) {
                    horizontalScroll(touchPointF)
                    findItemViewHolderUnderRaw(touchPointF.x, touchPointF.y)?.also { newVH ->
                        draggingItemVH?.also { draggingVH ->
                            // we need to know which recyclerview to give vertical scroll so it
                            // knows what to scroll
                            // the problem is we don't yet have a way of associating ItemVHs with
                            // ListAdapters or RecyclerViews, we can (and should) do this by
                            // assigning IDs to adapters
                            verticalScroll(touchPointF)
                            // swapBoardViewHoldersView(draggingVH, newVH)
                        }
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                draggingItemVH = findItemViewHolderUnderRaw(touchPointF.x, touchPointF.y)
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
                            // swapBoardViewHoldersView(draggingVH, newVH)
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

    inline fun findItemViewHolderUnderRaw(rawX: Float, rawY: Float): BoardViewItemVH? {
        return findBoardViewHolderUnderRaw(rawX, rawY)?.let { boardVH ->
            boardVH.list?.let { boardList ->
                boardList.findChildViewUnderRaw(rawX, rawY)?.let { view ->
                    boardList.getChildViewHolder(view) as? BoardViewItemVH
                }
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
    }

    fun swapBoardViewHoldersView(oldVH: BoardViewColumnVH, newVH: BoardViewColumnVH) {
        val swap = ViewHolderSwap(oldVH, newVH)
        if (newVH != oldVH && !boardVHSwaps.containsKey(swap)) {
            boardVHSwaps[swap] = false
        }
        if (newVH != oldVH && boardVHSwaps[swap] == false
                && boardView.itemAnimator?.isRunning == false) {
            adapter?.onSwapBoardViewHolders(oldVH, newVH)
            // below getting called too many times because our update rate is too fast for the
            // Choreographer or animator!
            swapBoardViewHoldersAdapter(oldVH, newVH)
            doAfterFinishAnimation {
                boardVHSwaps[swap] = true
                boardVHSwaps.remove(swap)
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

    fun verticalScroll(touchPoint: PointF) {
        // we need to know which recycler view we should be scrolling for
    }

    inline fun doAfterFinishAnimation(crossinline block: (BoardView) -> Unit) {
        boardView.itemAnimator?.isRunning { block(boardView) }
    }
}

// Represents a ViewHolder Swap which we can use to track if swaps are completed
private data class ViewHolderSwap<VH : BaseViewHolder>(val old: VH, val new: VH)