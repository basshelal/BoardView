@file:Suppress("RedundantVisibilityModifier")

package com.github.basshelal.boardview

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_boardcolumn.view.*
import kotlin.math.floor
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

    // Horizontal Scrolling info, transient shit
    private val interpolator = LogarithmicInterpolator()
    private val updateRatePerMilli = floor(millisPerFrame)
    private val horizontalMaxScrollBy = (updateRatePerMilli * 2F).roundToInt()
    private var horizontalScrollBoundWidth = 0F
    private val outsideLeftScrollBounds = RectF()
    private val leftScrollBounds = RectF()
    private val outsideRightScrollBounds = RectF()
    private val rightScrollBounds = RectF()

    private val snapHelper = PagerSnapHelper()

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

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        horizontalScrollBoundWidth = this.globalVisibleRectF.width() / 5F
        outsideLeftScrollBounds.set(this.globalVisibleRectF.also {
            it.right = it.left
            it.left = 0F
        })
        leftScrollBounds.set(this.globalVisibleRectF.also {
            it.right = it.left + horizontalScrollBoundWidth
        })
        outsideRightScrollBounds.set(this.globalVisibleRectF.also {
            it.left = it.right
            it.right = realScreenWidth.F
        })
        rightScrollBounds.set(this.globalVisibleRectF.also {
            it.left = it.right - horizontalScrollBoundWidth
        })
    }

    fun horizontalScroll(touchPoint: PointF) {
        var scrollBy = 0
        when (touchPoint) {
            in leftScrollBounds -> {
                val multiplier = interpolator[
                        1F - (touchPoint.x - leftScrollBounds.left) / (leftScrollBounds.right - leftScrollBounds.left)]
                scrollBy = -(horizontalMaxScrollBy * multiplier).roundToInt()
            }
            in rightScrollBounds -> {
                val multiplier = interpolator[
                        (touchPoint.x - rightScrollBounds.left) / (rightScrollBounds.right - rightScrollBounds.left)]
                scrollBy = (horizontalMaxScrollBy * multiplier).roundToInt()
            }
            in outsideLeftScrollBounds -> scrollBy = -horizontalMaxScrollBy
            in outsideRightScrollBounds -> scrollBy = horizontalMaxScrollBy
        }
        this.scrollBy(scrollBy, 0)
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
    public fun switchToSingleColumnModeAt(adapterPosition: Int, animationListener: Animation.AnimationListener) {
        // Caller didn't check their position was valid :/
        if (adapterPosition > (boardAdapter?.itemCount ?: -1) || adapterPosition < 0) return
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

    public fun switchToMultiColumnMode(newColumnWidth: Int, animationListener: Animation.AnimationListener) {
        (allVisibleViewHolders.first() as? BoardColumnViewHolder)?.also { columnVH ->
            // Initial count of children, should be 1
            val initialChildCount = childCount
            var currentChildCount = initialChildCount

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
                            if (childCount > currentChildCount) {
                                // A new child appears!
                                // We can't know anything about the new child so we brute force all possibilities
                                children.filter { it != columnVH.itemView }
                                        .forEach { newChildren.putIfAbsentSafe(it, false) }

                                // Below guarantees that each new child gets animated only once
                                newChildren.forEach { (view, animated) ->
                                    if (!animated) {
                                        animateNewlyAppearedChild(view, newColumnWidth,
                                                (animationDuration.F * (1.0F - interpolatedTime)).L)
                                        newChildren[view] = true
                                    }
                                }
                                currentChildCount = childCount
                            }
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

    /**
     * The passed in [adapter] must be a descendant of [BoardAdapter].
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardAdapter) super.setAdapter(adapter)
        else if (adapter != null)
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
            savedState.layoutStates = boardAdapter.layoutStates.values.toList()
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
        boardAdapter?.layoutStates?.also { hashMap ->
            state.layoutStates?.also {
                it.forEachIndexed { index, linearState ->
                    hashMap[index] = linearState
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
     * The layout states of each [BoardColumnViewHolder] with its adapter position as the key
     */
    // TODO: 26-Mar-20 Replace with ArrayList??
    internal val layoutStates = HashMap<Int, LinearState>()

    // We need one of these to notify the layoutStates that something changed and we need to
    // update that
    // TODO: 26-Mar-20 Implement this!
    private val dataObserver = object : RecyclerView.AdapterDataObserver() {

        override fun onChanged() {
            //logE("Something Changed!")
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            //logE("Moved $itemCount from $fromPosition to $toPosition")
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            //logE("Changed $itemCount items from $positionStart")
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            //logE("Inserted $itemCount items from $positionStart")
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            //logE("Removed $itemCount items from $positionStart")
        }
    }

    @CallSuper
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (recyclerView is BoardView) {
            super.onAttachedToRecyclerView(recyclerView)
            this.columnWidth = recyclerView.columnWidth
            registerAdapterDataObserver(dataObserver)
        }
    }

    @CallSuper
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        unregisterAdapterDataObserver(dataObserver)
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
        layoutStates[holder.adapterPosition].also {
            if (it == null) {
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
            layoutStates[holder.adapterPosition] = it
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

    var layoutStates: List<LinearState>? = null
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