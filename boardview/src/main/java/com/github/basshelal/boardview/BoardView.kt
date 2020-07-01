@file:Suppress("RedundantVisibilityModifier", "NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.AbsSavedState
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import androidx.annotation.CallSuper
import androidx.annotation.Px
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.contains
import androidx.core.view.children
import androidx.core.view.get
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.BOTTOM
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.BOTTOM_INSIDE_LEFT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.BOTTOM_INSIDE_RIGHT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.BOTTOM_OUTSIDE_LEFT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.BOTTOM_OUTSIDE_RIGHT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.ERROR
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.INSIDE
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.INSIDE_LEFT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.INSIDE_RIGHT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.OUTSIDE_LEFT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.OUTSIDE_RIGHT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.TOP
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.TOP_INSIDE_LEFT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.TOP_INSIDE_RIGHT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.TOP_OUTSIDE_LEFT
import com.github.basshelal.boardview.BoardView.BoardViewBounds.Sector.TOP_OUTSIDE_RIGHT
import com.github.basshelal.boardview.utils.BaseAdapter
import com.github.basshelal.boardview.utils.BaseRecyclerView
import com.github.basshelal.boardview.utils.BaseViewHolder
import com.github.basshelal.boardview.utils.Beta
import com.github.basshelal.boardview.utils.F
import com.github.basshelal.boardview.utils.I
import com.github.basshelal.boardview.utils.L
import com.github.basshelal.boardview.utils.LinearState
import com.github.basshelal.boardview.utils.LogarithmicInterpolator
import com.github.basshelal.boardview.utils.SaveRestoreLinearLayoutManager
import com.github.basshelal.boardview.utils.animation
import com.github.basshelal.boardview.utils.animationListener
import com.github.basshelal.boardview.utils.canScrollHorizontally
import com.github.basshelal.boardview.utils.copy
import com.github.basshelal.boardview.utils.dpToPx
import com.github.basshelal.boardview.utils.findChildViewUnderRaw
import com.github.basshelal.boardview.utils.firstVisibleViewHolder
import com.github.basshelal.boardview.utils.get
import com.github.basshelal.boardview.utils.globalVisibleRect
import com.github.basshelal.boardview.utils.globalVisibleRectF
import com.github.basshelal.boardview.utils.isAdapterPositionNotValid
import com.github.basshelal.boardview.utils.isAdapterPositionValid
import com.github.basshelal.boardview.utils.lastVisibleViewHolder
import com.github.basshelal.boardview.utils.logE
import com.github.basshelal.boardview.utils.millisPerFrame
import com.github.basshelal.boardview.utils.putIfAbsentSafe
import com.github.basshelal.boardview.utils.show
import com.github.basshelal.boardview.utils.updateLayoutParamsSafe
import kotlinx.android.synthetic.main.view_boardcolumn.view.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class BoardView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    public inline val boardAdapter: BoardAdapter? get() = this.adapter as? BoardAdapter

    /**
     * The width of each column in pixels.
     * [WRAP_CONTENT] is not allowed.
     * [MATCH_PARENT] is allowed and will be resolved to the value returned by [getWidth].
     */
    @Px
    public var columnWidth = context.dpToPx(150).I
        set(value) {
            if (value < 0 && value != MATCH_PARENT)
                throw IllegalArgumentException("Column width must be " +
                        "greater than 0 or MATCH_PARENT (-1), passed in $value")
            val valid = if (value == MATCH_PARENT) this.width else value
            field = valid
            boardAdapter?.columnWidth = valid
            allVisibleViewHolders.forEach { it.itemView.updateLayoutParamsSafe { width = valid } }
        }

    public var isSnappingToItems: Boolean = false
        set(value) {
            field = value
            snapHelper.attachToRecyclerView(if (value) this else null)
        }

    /**
     * This takes into account if the LayoutManager is using reverse layout, this is important
     * for [BoardView] because it is horizontal
     */
    inline val boardLayoutDirection: Int
        get() {
            return when (layoutDirection) {
                LAYOUT_DIRECTION_LTR ->
                    if (layoutManager?.reverseLayout == true)
                        LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
                LAYOUT_DIRECTION_RTL ->
                    if (layoutManager?.reverseLayout == true)
                        LAYOUT_DIRECTION_LTR else LAYOUT_DIRECTION_RTL
                else -> throw IllegalStateException("Invalid Layout Direction: $layoutDirection")
            }
        }

    // Horizontal Scrolling info, transient shit
    private val interpolator = LogarithmicInterpolator()
    private val updateRatePerMilli = floor(millisPerFrame)
    private val horizontalMaxScrollBy = (updateRatePerMilli * 2F).roundToInt()
    private val bounds = BoardViewBounds(this.globalVisibleRectF)
    private val snapHelper = PagerSnapHelper()

    // This receives any notify events the caller sends so that we can properly save the layout
    // states when the adapter's contents change
    private val layoutStatesDataObserver = object : RecyclerView.AdapterDataObserver() {

        override fun onChanged() {
            // We don't know what changed, so do we reset everything??
            boardAdapter?.layoutStates?.clear()
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            boardAdapter?.layoutStates?.also { list ->
                ArrayList(list.subList(min(fromPosition, toPosition),
                        max(toPosition, fromPosition))).also { range ->
                    range.forEach { list.remove(it) }
                    list.addAll(toPosition, range)
                }
            }
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            boardAdapter?.also { boardAdapter ->
                (positionStart..(positionStart + itemCount)).forEach {
                    (findViewHolderForAdapterPosition(it) as? BoardColumnViewHolder)?.also { holder ->
                        holder.list?.layoutManager?.saveState()?.also {
                            boardAdapter.layoutStates[holder.adapterPosition] = it
                        }
                    }
                }
            }
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            boardAdapter?.layoutStates?.addAll(positionStart, List(itemCount) { null })
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            (positionStart..(positionStart + itemCount)).forEach {
                boardAdapter?.layoutStates?.removeAt(it)
            }
        }
    }

    init {
        this.setHasFixedSize(true)
        layoutManager = SaveRestoreLinearLayoutManager(context).also {
            it.orientation = HORIZONTAL
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 6
        }
        isHorizontalScrollBarEnabled = true
        isVerticalScrollBarEnabled = false
    }

    override fun dispatchDraw(canvas: Canvas?) {
        /*
         * We're doing this because of the below exception that is out of our control:
         * java.lang.NullPointerException: Attempt to read from field
         * 'int android.view.View.mViewFlags' on a null object reference at
         * android.view.ViewGroup.dispatchDraw(ViewGroup.java:4111)
         */
        try {
            super.dispatchDraw(canvas)
        } catch (e: NullPointerException) {
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        bounds.set(this.globalVisibleRectF)
    }

    fun horizontalScroll(touchPoint: PointF) {
        val scrollBy: Int
        when (bounds.findSectorForPoint(touchPoint)) {
            INSIDE_LEFT, TOP_INSIDE_LEFT, BOTTOM_INSIDE_LEFT -> {
                val multiplier = interpolator[1F -
                        (touchPoint.x - bounds.scrollLeft.left) /
                        (bounds.scrollLeft.right - bounds.scrollLeft.left)]
                scrollBy = -(horizontalMaxScrollBy * multiplier).roundToInt()
            }
            INSIDE_RIGHT, TOP_INSIDE_RIGHT, BOTTOM_INSIDE_RIGHT -> {
                val multiplier = interpolator[
                        (touchPoint.x - bounds.scrollRight.left) /
                                (bounds.scrollRight.right - bounds.scrollRight.left)]
                scrollBy = (horizontalMaxScrollBy * multiplier).roundToInt()
            }
            OUTSIDE_LEFT, TOP_OUTSIDE_LEFT, BOTTOM_OUTSIDE_LEFT -> {
                scrollBy = -horizontalMaxScrollBy
            }
            OUTSIDE_RIGHT, TOP_OUTSIDE_RIGHT, BOTTOM_OUTSIDE_RIGHT -> {
                scrollBy = horizontalMaxScrollBy
            }
            else -> return
        }
        this.scrollBy(scrollBy, 0)
    }

    private inline fun viewHolderUnderRaw(pointF: PointF): BoardColumnViewHolder? {
        val view = findChildViewUnderRaw(pointF)
                ?: findChildViewUnderRaw(pointF.copy { y = this@BoardView.globalVisibleRectF.top + 1 })
                ?: findChildViewUnderRaw(pointF.copy { y = this@BoardView.globalVisibleRectF.bottom - 1 })
        return view?.let { getChildViewHolder(it) as? BoardColumnViewHolder }
    }

    internal fun getViewHolderUnder(point: PointF): BoardColumnViewHolder? {
        return when (bounds.findSectorForPoint(point)) {
            OUTSIDE_LEFT, TOP_OUTSIDE_LEFT, BOTTOM_OUTSIDE_LEFT -> when (boardLayoutDirection) {
                LAYOUT_DIRECTION_LTR -> firstVisibleViewHolder as? BoardColumnViewHolder
                LAYOUT_DIRECTION_RTL -> lastVisibleViewHolder as? BoardColumnViewHolder
                else -> null
            }
            OUTSIDE_RIGHT, TOP_OUTSIDE_RIGHT, BOTTOM_OUTSIDE_RIGHT -> when (boardLayoutDirection) {
                LAYOUT_DIRECTION_LTR -> lastVisibleViewHolder as? BoardColumnViewHolder
                LAYOUT_DIRECTION_RTL -> firstVisibleViewHolder as? BoardColumnViewHolder
                else -> null
            }
            TOP, TOP_INSIDE_LEFT, TOP_INSIDE_RIGHT ->
                viewHolderUnderRaw(point.copy { y = this@BoardView.globalVisibleRectF.top + 1 })
            BOTTOM, BOTTOM_INSIDE_LEFT, BOTTOM_INSIDE_RIGHT ->
                viewHolderUnderRaw(point.copy { y = this@BoardView.globalVisibleRectF.bottom - 1 })
            else -> viewHolderUnderRaw(point)
        }
    }

    @Beta
    public inline fun switchToSingleColumnModeAt(adapterPosition: Int,
                                                 crossinline onStartAnimation: (Animation) -> Unit = {},
                                                 crossinline onRepeatAnimation: (Animation) -> Unit = {},
                                                 crossinline onEndAnimation: (Animation) -> Unit = {}) =
            switchToSingleColumnModeAt(adapterPosition,
                    animationListener(onStartAnimation, onRepeatAnimation, onEndAnimation))

    /**
     * This will switch to single column mode at the passed in [adapterPosition].
     *
     * Single Column Mode allows you to simulate a single column at once without inflating a new
     * View or switching to a new Fragment or Activity. This way, the entire [BoardView] will
     * look and feel exactly the same and maintain the same state.
     *
     * The [adapterPosition] must be a position from a ViewHolder that is currently visible
     * otherwise nothing will happen
     */
    // TODO: 07-Apr-20 Animations are sluggish!
    @Beta
    public fun switchToSingleColumnModeAt(adapterPosition: Int, animationListener: Animation.AnimationListener) {
        // Caller didn't check their position was valid :/
        if (boardAdapter?.isAdapterPositionNotValid(adapterPosition) == true) return
        (findViewHolderForAdapterPosition(adapterPosition) as? BoardColumnViewHolder)?.also { columnVH ->
            // Disable over-scrolling temporarily and reset it to what it was after the animation is done
            val overScrolling = this.isOverScrollingEnabled
            isOverScrollingEnabled = false
            // We need to get these so that we can call notifyItemChanged on them after the animation ends
            val viewHolders = List(boardAdapter?.itemCount ?: adapterPosition + 10) { it }
            val targetWidth = this.globalVisibleRect.width()
            val scrollByAmount = columnVH.itemView.x
            // We use this to get a deltaTime
            var oldInterpolatedTime = 0F
            columnVH.itemView.startAnimation(
                    animation { interpolatedTime: Float, _ ->
                        val dTime = interpolatedTime - oldInterpolatedTime
                        columnVH.itemView.updateLayoutParamsSafe {
                            if (scrollByAmount > 0) scrollBy((scrollByAmount * dTime).roundToInt(), 0)
                            if (width < targetWidth) width += ((targetWidth - width).F * interpolatedTime).roundToInt()
                        }
                        oldInterpolatedTime = interpolatedTime
                    }.also {
                        it.interpolator = AccelerateInterpolator(2.0F)
                        it.duration = 500L
                        it.setAnimationListener(
                                animationListener(
                                        onStart = { animationListener.onAnimationStart(it) },
                                        onRepeat = { animationListener.onAnimationRepeat(it) },
                                        onEnd = {
                                            animationListener.onAnimationEnd(it)
                                            columnWidth = MATCH_PARENT
                                            // We need to call this to maintain
                                            // position after the update
                                            // RV thinks we're at somewhere else
                                            scrollToPosition(adapterPosition)
                                            viewHolders.filter { it != adapterPosition }
                                                    .forEach { boardAdapter?.notifyItemChanged(it) }
                                            isSnappingToItems = true
                                            isOverScrollingEnabled = overScrolling
                                        }
                                )
                        )
                    }
            )
        }
    }

    @Beta
    public inline fun switchToMultiColumnMode(newColumnWidth: Int,
                                              crossinline onStartAnimation: (Animation) -> Unit = {},
                                              crossinline onRepeatAnimation: (Animation) -> Unit = {},
                                              crossinline onEndAnimation: (Animation) -> Unit = {}) =
            switchToMultiColumnMode(newColumnWidth,
                    animationListener(onStartAnimation, onRepeatAnimation, onEndAnimation))

    // TODO: 07-Apr-20 Animations are sluggish!
    @Beta
    public fun switchToMultiColumnMode(newColumnWidth: Int, animationListener: Animation.AnimationListener) {
        (allVisibleViewHolders.first() as? BoardColumnViewHolder)?.also { columnVH ->
            // Initial count of children, should be 1 since we're in Single Column Mode
            var currentChildCount = childCount

            // Key: Child View Value: hasAnimated
            val newChildren = HashMap<View, Boolean>()

            // Guess which VHs we will need, overestimate for safety!
            val cacheAmount = layoutManager?.initialPrefetchItemCount ?: 10
            val viewHolders = ((columnVH.adapterPosition - cacheAmount)..
                    (columnVH.adapterPosition + cacheAmount)).toList()

            val initialWidth = columnVH.itemView.width
            val widthDifference = newColumnWidth.F - initialWidth.F
            val animationDuration = 500
            isSnappingToItems = false

            columnVH.itemView.startAnimation(
                    animation { interpolatedTime: Float, _ ->
                        columnVH.itemView.updateLayoutParamsSafe {
                            if (width > newColumnWidth) {
                                width = initialWidth - (widthDifference * -interpolatedTime).I
                            }
                        }
                        if (childCount > currentChildCount) {
                            // A new child appears!
                            // We can't know anything about the new child so we brute force all possibilities
                            children.filter { it != columnVH.itemView }
                                    .forEach { newChildren.putIfAbsentSafe(it, false) }

                            // Below guarantees that each new child gets animated only once
                            newChildren.forEach { (view, hasAnimated) ->
                                if (!hasAnimated) {
                                    animateNewlyAppearedChild(view, newColumnWidth,
                                            (animationDuration.F * (1.0F - interpolatedTime)).L)
                                    newChildren[view] = true
                                }
                            }
                            currentChildCount = childCount
                        }
                    }.also {
                        it.interpolator = DecelerateInterpolator(0.75F)
                        it.duration = animationDuration.L
                        it.setAnimationListener(
                                animationListener(
                                        onStart = { animationListener.onAnimationStart(it) },
                                        onRepeat = { animationListener.onAnimationRepeat(it) },
                                        onEnd = {
                                            animationListener.onAnimationEnd(it)
                                            columnWidth = newColumnWidth
                                            // Safety measure but only on the non-visible VHs so this is invisible
                                            viewHolders.filter { it !in allVisibleViewHolders.map { it.adapterPosition } }
                                                    .forEach { boardAdapter?.notifyItemChanged(it) }
                                        }
                                )
                        )
                    }
            )
        }
    }

    // A child has appeared! Animate its collapse
    private inline fun animateNewlyAppearedChild(view: View, newColumnWidth: Int, duration: Long) {
        if (duration <= 0) return
        val initialWidth = view.width
        val widthDifference = newColumnWidth.F - initialWidth.F

        view.startAnimation(
                animation { interpolatedTime: Float, _ ->
                    view.updateLayoutParamsSafe {
                        width = initialWidth - (widthDifference * -interpolatedTime).I
                    }
                }.also {
                    it.interpolator = DecelerateInterpolator(0.75F)
                    it.duration = duration
                }
        )
    }

    /* Weird shit happens whenever we do a swap with an item at layout position 0,
     * This is because of how LinearLayoutManager works, it ends up scrolling for us even
     * though we never told it to, see more here
     * https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
     * So we solve this by forcing it back where it was, essentially cancelling the
     * scroll it did
     * This is fully done for us in LinearLayoutManager.prepareForDrop()
     */
    internal inline fun prepareForDrop(draggingColumn: BoardColumnViewHolder,
                                       targetColumn: BoardColumnViewHolder) {
        if (canScrollHorizontally &&
                (draggingColumn.layoutPosition == 0 || targetColumn.layoutPosition == 0 ||
                        this[0] == draggingColumn.itemView || this[0] == targetColumn.itemView)) {
            layoutManager?.prepareForDrop(draggingColumn.itemView, targetColumn.itemView, 0, 0)
        }
    }

    /**
     * The passed in [adapter] must be a descendant of [BoardAdapter].
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardAdapter) {
            super.setAdapter(adapter)
            adapter.registerAdapterDataObserver(layoutStatesDataObserver)
        } else if (adapter != null)
            logE("BoardView adapter must be a descendant of BoardAdapter!\n" +
                    "passed in adapter is of type ${adapter::class.simpleName}")
    }

    /**
     * Saves the layout of this [RecyclerView] (provided by default) and also saves the layout of
     * every [BoardList] contained in this [BoardView], even those that are not currently visible.
     * This is because the layout states of all the [BoardList]s is saved in
     * [BoardAdapter.layoutStates].
     *
     * @return the [BoardViewSavedState] of this [BoardView]
     */
    @CallSuper
    open fun saveState(): BoardViewSavedState {
        val savedState = BoardViewSavedState(super.onSaveInstanceState() as? RecyclerViewState)
        boardAdapter?.also { boardAdapter ->
            allVisibleViewHolders.forEach {
                (it as? BoardColumnViewHolder)?.also { holder ->
                    holder.list?.layoutManager?.saveState()?.also {
                        boardAdapter.layoutStates[holder.adapterPosition] = it
                    }
                }
            }
            savedState.layoutStates = boardAdapter.layoutStates.toList()
            savedState.columnWidth = this.columnWidth
            savedState.isSnappingToItems = this.isSnappingToItems
        }
        return savedState
    }

    /**
     * Restores the layout of this [RecyclerView] (provided by default) and also restores the
     * layout of every [BoardList] contained in this [BoardView], even those that are not
     * currently visible.
     * This is because the layout states of all the [BoardList]s is saved in
     * [BoardAdapter.layoutStates].
     * The current state of this [BoardView] is not stored internally, call [saveState] to
     * retrieve the latest [BoardViewSavedState]
     */
    @CallSuper
    open fun restoreFromState(state: BoardViewSavedState) {
        super.onRestoreInstanceState(state.savedState)
        boardAdapter?.layoutStates?.also { list ->
            state.layoutStates?.also {
                it.forEachIndexed { index, linearState ->
                    list[index] = linearState
                }
            }
        }
        this.columnWidth = state.columnWidth
        this.isSnappingToItems = state.isSnappingToItems
    }

    @SuppressLint("MissingSuperCall") // we called super in saveState()
    override fun onSaveInstanceState(): Parcelable? = saveState()

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is BoardViewSavedState) restoreFromState(state)
        else super.onRestoreInstanceState(state)
    }

    private class BoardViewBounds(globalRectF: RectF) {

        var horizontalScrollBoundsWidth = globalRectF.width() / 5F

        // Rectangles
        var inside = globalRectF
        val scrollLeft = RectF()
        val scrollRight = RectF()
        val top = RectF()
        val bottom = RectF()
        val left = RectF()
        val right = RectF()

        init {
            set(globalRectF)
        }

        inline fun set(globalRectF: RectF) {
            horizontalScrollBoundsWidth = globalRectF.width() / 5F
            inside.set(globalRectF)
            scrollLeft.set(globalRectF.copy {
                right = left + horizontalScrollBoundsWidth
                top = 0F
                bottom = Float.MAX_VALUE
            })
            scrollRight.set(globalRectF.copy {
                left = right - horizontalScrollBoundsWidth
                top = 0F
                bottom = Float.MAX_VALUE
            })
            top.set(globalRectF.copy {
                bottom = top
                top = 0F
                left = 0F
                right = Float.MAX_VALUE
            })
            bottom.set(globalRectF.copy {
                top = bottom
                bottom = Float.MAX_VALUE
                left = 0F
                right = Float.MAX_VALUE
            })
            left.set(globalRectF.copy {
                right = left
                left = 0F
                top = 0F
                bottom = Float.MAX_VALUE
            })
            right.set(globalRectF.copy {
                left = right
                right = Float.MAX_VALUE
                top = 0F
                bottom = Float.MAX_VALUE
            })
        }

        inline fun showAll(view: View) {
            scrollLeft.show(view, Color.RED)
            scrollRight.show(view, Color.BLUE)
            inside.show(view, Color.BLACK)
            top.show(view, Color.GREEN)
            bottom.show(view, Color.YELLOW)
            left.show(view, Color.CYAN)
            right.show(view, Color.MAGENTA)
        }

        inline fun findSectorForPoint(point: PointF): Sector {
            return when (point) {
                in left -> when (point) {
                    in top -> TOP_OUTSIDE_LEFT
                    in bottom -> BOTTOM_OUTSIDE_LEFT
                    else -> OUTSIDE_LEFT
                }
                in scrollLeft -> when (point) {
                    in top -> TOP_INSIDE_LEFT
                    in bottom -> BOTTOM_INSIDE_LEFT
                    else -> INSIDE_LEFT
                }
                in scrollRight -> when (point) {
                    in top -> TOP_INSIDE_RIGHT
                    in bottom -> BOTTOM_INSIDE_RIGHT
                    else -> INSIDE_RIGHT
                }
                in right -> when (point) {
                    in top -> TOP_OUTSIDE_RIGHT
                    in bottom -> BOTTOM_OUTSIDE_RIGHT
                    else -> OUTSIDE_RIGHT
                }
                in top -> TOP
                in bottom -> BOTTOM
                in inside -> INSIDE
                else -> ERROR
            }
        }

        enum class Sector {
            TOP_OUTSIDE_LEFT, TOP_INSIDE_LEFT, TOP, TOP_INSIDE_RIGHT, TOP_OUTSIDE_RIGHT,
            OUTSIDE_LEFT, INSIDE_LEFT, INSIDE, INSIDE_RIGHT, OUTSIDE_RIGHT,
            BOTTOM_OUTSIDE_LEFT, BOTTOM_INSIDE_LEFT, BOTTOM, BOTTOM_INSIDE_RIGHT, BOTTOM_OUTSIDE_RIGHT,
            ERROR
        }
    }
}

abstract class BoardAdapter(
        var adapter: BoardContainerAdapter? = null
) : BaseAdapter<BoardColumnViewHolder>() {

    // Ideally we wouldn't want this but we don't want to not keep a reference of the BoardView!
    internal var columnWidth: Int = 0

    /**
     * The layout states of each [BoardColumnViewHolder]
     */
    internal val layoutStates = ArrayList<LinearState?>()

    @CallSuper
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (recyclerView is BoardView) {
            super.onAttachedToRecyclerView(recyclerView)
            this.columnWidth = recyclerView.columnWidth
            layoutStates.clear()
            layoutStates.addAll(List(itemCount) { null })
        }
    }

    // We handle the creation because we return BoardVH that contains columns
    // We have to do this ourselves because we resolve the header, footer and list layout as well
    // as managing list adapters
    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardColumnViewHolder {
        val column = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_boardcolumn, parent, false) as ConstraintLayout
        val viewHolder = BoardColumnViewHolder(column)
        val isListWrapContent = adapter?.isListWrapContent ?: false
        column.updateLayoutParamsSafe {
            width = columnWidth
            height = if (isListWrapContent) WRAP_CONTENT else MATCH_PARENT
        }
        viewHolder.list = column.boardListView
        adapter?.onCreateListHeader(column)?.also {
            viewHolder.header = it
            column.addView(it)
            it.updateLayoutParamsSafe<ConstraintLayout.LayoutParams> {
                width = it.layoutParams.width
                height = it.layoutParams.height
            }
            column.boardListView?.updateLayoutParamsSafe<ConstraintLayout.LayoutParams> {
                if (adapter?.isHeaderPadded == true) {
                    topToTop = ConstraintLayout.LayoutParams.UNSET
                    topToBottom = column.header_frameLayout.id
                } else {
                    topToBottom = ConstraintLayout.LayoutParams.UNSET
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                }
            }
        }
        adapter?.onCreateFooter(column)?.also {
            viewHolder.footer = it
            column.footer_frameLayout.addView(it)
            column.boardListView?.updateLayoutParamsSafe<ConstraintLayout.LayoutParams> {
                if (adapter?.isFooterPadded == true) {
                    bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                    bottomToTop = column.footer_frameLayout.id
                } else {
                    bottomToTop = ConstraintLayout.LayoutParams.UNSET
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
            }
        }
        column.boardListView?.updateLayoutParamsSafe<ConstraintLayout.LayoutParams> {
            constrainedHeight = true
            if (isListWrapContent) {
                height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                column.boardListView?.setHasFixedSize(false)
            } else {
                height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                column.boardListView?.setHasFixedSize(true)
            }
        }
        onViewHolderCreated(viewHolder)
        return viewHolder
    }

    // callback for caller to do stuff after onCreateViewHolder is called
    open fun onViewHolderCreated(holder: BoardColumnViewHolder) {}

    @CallSuper
    override fun onBindViewHolder(holder: BoardColumnViewHolder, position: Int) {
        holder.itemView.updateLayoutParamsSafe { width = columnWidth }
        holder.list?.adapter.also { current ->
            if (current == null) {
                adapter?.onCreateListAdapter(position)?.also {
                    holder.list?.adapter = it
                }
            } else {
                holder.boardListAdapter?.bindAdapter(holder, position)
                holder.list?.notifyAllItemsChanged()
            }
        }
    }

    @CallSuper
    override fun onViewAttachedToWindow(holder: BoardColumnViewHolder) {
        if (holder.adapterPosition > layoutStates.lastIndex) {
            layoutStates.add(null)
        }
        layoutStates[holder.adapterPosition].also {
            if (it == null && holder.isAdapterPositionValid) {
                holder.list?.layoutManager?.saveState()?.also {
                    holder.list?.layoutManager?.scrollToPosition(0)
                    layoutStates[holder.adapterPosition] = it
                }
            } else {
                holder.list?.layoutManager?.restoreState(it)
            }
        }
    }

    @CallSuper
    override fun onViewDetachedFromWindow(holder: BoardColumnViewHolder) {
        holder.list?.layoutManager?.saveState()?.also {
            if (holder.isAdapterPositionValid) layoutStates[holder.adapterPosition] = it
        }
    }
}

open class BoardColumnViewHolder(itemView: View) : BaseViewHolder(itemView) {
    var header: View? = null
        internal set
    var list: BoardList? = null
        internal set
    var footer: View? = null
        internal set
    inline val boardListAdapter: BoardListAdapter<*>? get() = list?.boardListAdapter
}

internal typealias RecyclerViewState = RecyclerView.SavedState

open class BoardViewSavedState(val savedState: RecyclerViewState?) : AbsSavedState(savedState) {

    var layoutStates: List<LinearState?>? = null
    var columnWidth: Int = WRAP_CONTENT
    var isSnappingToItems: Boolean = false

    @CallSuper
    override fun writeToParcel(dest: Parcel?, flags: Int) {
        super.writeToParcel(dest, flags)
        dest?.also {
            it.writeTypedList(layoutStates)
            it.writeInt(columnWidth)
            it.writeInt(if (isSnappingToItems) 1 else 0)
        }
    }

    companion object CREATOR : Parcelable.Creator<BoardViewSavedState> {

        override fun createFromParcel(parcel: Parcel): BoardViewSavedState =
                BoardViewSavedState(parcel.readParcelable(
                        RecyclerViewState::class.java.classLoader)).also {
                    val list = ArrayList<LinearState>()
                    parcel.readTypedList(list, LinearState.CREATOR)
                    it.layoutStates = list
                    it.columnWidth = parcel.readInt()
                    it.isSnappingToItems = parcel.readInt() == 1
                }

        override fun newArray(size: Int): Array<BoardViewSavedState?> = arrayOfNulls(size)
    }
}