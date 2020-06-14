@file:Suppress("RedundantVisibilityModifier", "NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
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
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginStart
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
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
     * The width of every column in pixels.
     * [WRAP_CONTENT] is not allowed.
     * [MATCH_PARENT] is allowed and will be resolved to the value returned by [getWidth].
     */
    @Px
    public var columnWidth = context.convertDpToPx(150).I
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
    private var horizontalScrollBoundsInsideWidth = 0F

    private val outsideLeftScrollBounds = RectF()
    private val insideLeftScrollBounds = RectF()
    private val outsideRightScrollBounds = RectF()
    private val insideRightScrollBounds = RectF()
    private val outsideTopBounds = RectF()
    private val outsideBottomBounds = RectF()

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
        layoutManager = SaveRestoreLinearLayoutManager(context).also {
            it.orientation = HORIZONTAL
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 6
        }
        isHorizontalScrollBarEnabled = true
        isVerticalScrollBarEnabled = false
        this.setHasFixedSize(true)
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

        horizontalScrollBoundsInsideWidth = this.globalVisibleRectF.width() / 5F
        outsideLeftScrollBounds.set(this.globalVisibleRectF.also {
            it.right = it.left
            it.left = 0F
        })
        insideLeftScrollBounds.set(this.globalVisibleRectF.also {
            it.right = it.left + horizontalScrollBoundsInsideWidth
        })
        outsideRightScrollBounds.set(this.globalVisibleRectF.also {
            it.left = it.right
            it.right = realScreenWidth.F
        })
        insideRightScrollBounds.set(this.globalVisibleRectF.also {
            it.left = it.right - horizontalScrollBoundsInsideWidth
        })
        outsideTopBounds.set(this.globalVisibleRectF.also {
            it.bottom = it.top
            it.top = 0F
        })
        outsideBottomBounds.set(this.globalVisibleRectF.also {
            it.top = it.bottom
            it.bottom = realScreenHeight.F
        })
    }

    fun horizontalScroll(touchPoint: PointF) {
        var scrollBy = 0
        when (touchPoint) {
            in insideLeftScrollBounds -> {
                val multiplier = interpolator[1F -
                        (touchPoint.x - insideLeftScrollBounds.left) /
                        (insideLeftScrollBounds.right - insideLeftScrollBounds.left)]
                scrollBy = -(horizontalMaxScrollBy * multiplier).roundToInt()
            }
            in insideRightScrollBounds -> {
                val multiplier = interpolator[
                        (touchPoint.x - insideRightScrollBounds.left) /
                                (insideRightScrollBounds.right - insideRightScrollBounds.left)]
                scrollBy = (horizontalMaxScrollBy * multiplier).roundToInt()
            }
            in outsideLeftScrollBounds -> scrollBy = -horizontalMaxScrollBy
            in outsideRightScrollBounds -> scrollBy = horizontalMaxScrollBy
        }
        this.scrollBy(scrollBy, 0)
    }

    internal fun getViewHolderUnder(point: PointF): BoardColumnViewHolder? {
        return when {
            point in outsideTopBounds -> getViewHolderUnder(point.also { it.y = this.globalVisibleRectF.top + 1 })
            point in outsideBottomBounds -> getViewHolderUnder(point.also { it.y = this.globalVisibleRectF.bottom - 1 })
            (point in outsideLeftScrollBounds && boardLayoutDirection == LAYOUT_DIRECTION_LTR) ||
                    (point in outsideRightScrollBounds && boardLayoutDirection == LAYOUT_DIRECTION_RTL) ->
                layoutManager?.findFirstVisibleItemPosition()?.let { findViewHolderForAdapterPosition(it) as? BoardColumnViewHolder }
            (point in outsideLeftScrollBounds && boardLayoutDirection == LAYOUT_DIRECTION_RTL) ||
                    (point in outsideRightScrollBounds && boardLayoutDirection == LAYOUT_DIRECTION_LTR) ->
                layoutManager?.findLastVisibleItemPosition()?.let { findViewHolderForAdapterPosition(it) as? BoardColumnViewHolder }
            else -> findChildViewUnderRaw(point)?.let {
                getChildViewHolder(it) as? BoardColumnViewHolder
            }
        }
    }

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

    public inline fun switchToMultiColumnMode(newColumnWidth: Int,
                                              crossinline onStartAnimation: (Animation) -> Unit = {},
                                              crossinline onRepeatAnimation: (Animation) -> Unit = {},
                                              crossinline onEndAnimation: (Animation) -> Unit = {}) =
            switchToMultiColumnMode(newColumnWidth,
                    animationListener(onStartAnimation, onRepeatAnimation, onEndAnimation))

    // TODO: 07-Apr-20 Animations are sluggish!
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
    private fun animateNewlyAppearedChild(view: View, newColumnWidth: Int, duration: Long) {
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

    internal inline fun notifyColumnViewHoldersSwapped(draggingColumn: BoardColumnViewHolder,
                                                       targetColumn: BoardColumnViewHolder) {
        // From & To are guaranteed to be valid and different!
        val from = draggingColumn.adapterPosition
        val to = targetColumn.adapterPosition

        /* Weird shit happens whenever we do a swap with an item at layout position 0,
         * This is because of how LinearLayoutManager works, it ends up scrolling for us even
         * though we never told it to, see more here
         * https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
         * So we solve this by forcing it back where it was, essentially cancelling the
         * scroll it did
         */
        if (canScrollHorizontally && (draggingColumn.layoutPosition == 0 || targetColumn.layoutPosition == 0 ||
                        this[0] == draggingColumn.itemView || this[0] == targetColumn.itemView)) {
            layoutManager?.also { layoutManager ->
                firstVisibleViewHolder?.also { vh ->
                    val firstView = vh.itemView
                    var offset = 0
                    var margin = 0
                    when (boardLayoutDirection) {
                        View.LAYOUT_DIRECTION_LTR -> {
                            offset = layoutManager.getDecoratedLeft(firstView) -
                                    layoutManager.getLeftDecorationWidth(firstView)
                            margin = firstView.marginLeft
                        }
                        // TODO: 26-Mar-20 Figure out RTL and margins but
                        //  otherwise everything else is mostly correct
                        View.LAYOUT_DIRECTION_RTL -> {
                            offset = layoutManager.getDecoratedRight(firstView) -
                                    layoutManager.getRightDecorationWidth(firstView)
                            firstView.marginStart
                            margin = firstView.marginRight
                        }
                    }
                    val pos = vh.adapterPosition
                    boardAdapter?.notifyItemMoved(from, to)
                    layoutManager.scrollToPositionWithOffset(pos, offset)
                }
            }
        } else boardAdapter?.notifyItemMoved(from, to)
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
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_boardcolumn, parent, false) as ConstraintLayout
        val viewHolder = BoardColumnViewHolder(view)
        view.updateLayoutParamsSafe { width = columnWidth }
        viewHolder.list = view.boardListView
        // Header
        adapter?.onCreateListHeader(view)?.also {
            viewHolder.header = it
            view.header_frameLayout.addView(it)
            view.boardListView?.updateLayoutParamsSafe<ConstraintLayout.LayoutParams> {
                if (adapter?.isHeaderPadded == true) {
                    topToTop = ConstraintLayout.LayoutParams.UNSET
                    topToBottom = view.header_frameLayout.id
                } else {
                    topToBottom = ConstraintLayout.LayoutParams.UNSET
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                }
                height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            }
        }
        // Footer
        adapter?.onCreateFooter(view)?.also {
            viewHolder.footer = it
            view.footer_frameLayout.addView(it)
            viewHolder.list?.updateLayoutParamsSafe<ConstraintLayout.LayoutParams> {
                if (adapter?.isFooterPadded == true) {
                    bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                    bottomToTop = view.footer_frameLayout.id
                } else {
                    bottomToTop = ConstraintLayout.LayoutParams.UNSET
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
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

/**
 * ViewHolder for the [BoardColumn], these are used in [BoardView] and its adapter [BoardAdapter]
 */
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