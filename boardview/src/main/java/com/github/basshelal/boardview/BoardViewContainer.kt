@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
    var draggingBoardViewVH: BoardViewVH? = null

    val updateRatePerMilli = floor(millisPerFrame)
    val scrollRatePerMilli = 1F

    private val boardVHSwaps = HashMap<ViewHolderSwap<BoardViewVH>, Boolean>()
    private val itemVHSwaps = HashMap<ViewHolderSwap<BoardViewItemVH>, Boolean>()

    init {
        View.inflate(context, R.layout.container_boardviewcontainer, this)

        itemDragShadow()
        listDragShadow()
    }

    private inline fun itemDragShadow() {
        itemDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

            override fun onStartDrag(dragView: View) {
                draggingItemVH = findItemViewHolderUnderRaw(touchPointF.x, touchPointF.y)
                draggingItemVH?.itemView?.setBackgroundColor(Color.MAGENTA)
            }

            override fun onUpdateLocation(dragView: View, touchPoint: PointF) {
                findItemViewHolderUnderRaw(touchPoint.x, touchPoint.y)?.also { newVH ->
                    if (newVH != draggingItemVH) {
                        draggingItemVH?.itemView?.setBackgroundColor(Color.TRANSPARENT)
                        newVH.itemView.setBackgroundColor(Color.CYAN)
                        draggingItemVH = newVH
                    }
                }
            }

            override fun onEndDrag(dragView: View) {
                draggingItemVH = null
            }
        }
    }

    private inline fun listDragShadow() {
        listDragShadow.dragBehavior.dragListener = object : ObservableDragBehavior.SimpleDragListener() {

            val onNext = {
                if (draggingBoardViewVH != null) {
                    checkForHorizontalScroll(touchPointF)
                    findBoardViewHolderUnderRaw(touchPointF.x, touchPointF.y)?.also { newVH ->
                        draggingBoardViewVH?.also { draggingVH ->
                            swapBoardViewHoldersView(draggingVH, newVH)
                        }
                    }
                }
            }

            var disposable: Disposable? = null

            override fun onStartDrag(dragView: View) {
                draggingBoardViewVH = findBoardViewHolderUnderRaw(touchPointF.x, touchPointF.y)
                listDragShadow.isVisible = true
                draggingBoardViewVH?.itemView?.alpha = 1F
                disposable = Observable.interval(updateRatePerMilli.L, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.computation())
                        .subscribe { onNext() }
            }

            override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                disposable?.dispose()
            }

            override fun onEndDrag(dragView: View) {
                draggingBoardViewVH?.itemView?.alpha = 1F
                listDragShadow.isVisible = false
                draggingBoardViewVH = null
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

    inline fun findBoardViewHolderUnderRaw(rawX: Float, rawY: Float): BoardViewVH? {
        return boardView.findChildViewUnderRaw(rawX, rawY)?.let {
            boardView.getChildViewHolder(it) as? BoardViewVH
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

    inline fun startDraggingList(vh: BoardViewVH) {
        listDragShadow.updateToMatch(vh.itemView)
        listDragShadow.dragBehavior.startDrag()
    }

    fun swapItemViewHolders(old: BoardViewItemVH, new: BoardViewItemVH) {
        // TODO: 13-Mar-20 Do stuff when they're both in the same list
        // TODO: 13-Mar-20 Do more complicated stuff when they're both in different lists
    }

    fun swapBoardViewHoldersView(oldVH: BoardViewVH, newVH: BoardViewVH) {
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

    fun swapBoardViewHoldersAdapter(old: BoardViewVH, new: BoardViewVH) {
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

    fun checkForHorizontalScroll(touchPoint: PointF) {
        val scrollBy = updateRatePerMilli.I
        val width = boardView.globalVisibleRectF.width() / 6F
        val leftBounds = boardView.globalVisibleRectF.also { it.right = width }
        val rightBounds = boardView.globalVisibleRectF.also { it.left = it.width() - width }
        when {
            leftBounds.contains(touchPoint) -> {
                boardView.scrollBy(-scrollBy, 0)
            }
            rightBounds.contains(touchPoint) -> {
                boardView.scrollBy(scrollBy, 0)
            }
        }
    }

    inline fun doAfterFinishAnimation(crossinline block: (BoardView) -> Unit) {
        boardView.itemAnimator?.isRunning { block(boardView) }
    }
}

// Represents a ViewHolder Swap which we can use to track if swaps are completed
private data class ViewHolderSwap<VH : BaseViewHolder>(val old: VH, val new: VH)

abstract class BoardContainerAdapter {

    lateinit var boardViewContainer: BoardViewContainer
        internal set

    // The single Board Adapter, we're only doing this so that it can be customizable by caller
    // and also have all the functionality of a RecyclerView.Adapter
    abstract fun getBoardViewAdapter(): BoardAdapter

    // When we need new List Adapters! These are simple RecyclerView Adapters that will display
    // the Items, this is the caller's responsibility as these can be reused from existing code
    abstract fun onCreateListAdapter(position: Int): BoardListAdapter<*>

    /**
     * Called when a new BoardView Column is created
     * @return the header View or null if you do not want a header
     */
    abstract fun onCreateListHeader(parentView: ViewGroup): View?
    abstract fun onCreateFooter(parentView: ViewGroup): View?

    // Return true when the given boardListAdapter is correct for the position, false otherwise
    abstract fun matchListAdapter(boardListAdapter: BoardListAdapter<*>, position: Int): Boolean

    // Touch and Drag Shit callbacks here TODO

    open fun onSwapBoardViewHolders(old: BoardViewVH, new: BoardViewVH) {}
}