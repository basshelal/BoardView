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
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.contains
import androidx.core.view.postDelayed
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_boardcolumn.view.*
import kotlin.math.floor
import kotlin.math.roundToInt

class BoardView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    public inline val boardAdapter: BoardAdapter? get() = this.adapter as? BoardAdapter

    public var columnWidth = WRAP_CONTENT
        set(value) {
            field = value
            boardAdapter?.columnWidth = value
        }

    public var isSnappingToItems: Boolean = false
        set(value) {
            field = value
            snapHelper.attachToRecyclerView(if (value) this else null)
        }


    // Horizontal Scrolling info
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
            it.initialPrefetchItemCount = 5
        }
        isHorizontalScrollBarEnabled = true
        isVerticalScrollBarEnabled = false
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

    public inline fun displayColumnAt(adapterPosition: Int,
                                      crossinline onStartAnimation: (Animation) -> Unit = {},
                                      crossinline onRepeatAnimation: (Animation) -> Unit = {},
                                      crossinline onEndAnimation: (Animation) -> Unit = {}) =
            displayColumnAt(adapterPosition,
                    animationListener(onStartAnimation, onRepeatAnimation, onEndAnimation))

    // Here we will display the column at the position
    // Make sure the position is visible or at least close because behavior is not guaranteed
    // otherwise
    public fun displayColumnAt(adapterPosition: Int, animationListener: Animation.AnimationListener) {
        // TODO: 20-Mar-20 Scrolling isn't guaranteeing it's the one in the center! Hmmmm
        //  we may need to implement our own SmoothScroller based heavily onLinearSmoothScroller
        //  which will stop when the target View is in the center or beginning of the RecyclerView
        //  or even better! -> Our animation will ensure that it grows to fit no matter where the
        //  scroll ended, whether the target View was in the beginning or end or middle whatever
        //  This is because it will look nicer but also it guarantees that it looks correct no
        //  matter which adapterPosition is passed, the first one or the last one or anything in
        //  the middle
        if (adapterPosition > (boardAdapter?.itemCount ?: -1) || adapterPosition < 0) return
        smoothScrollToPosition(adapterPosition)
        doOnFinishScroll {
            // Wait a little to let scroller do it's thing and not make things seem too abrupt
            postDelayed(250L) {
                (findViewHolderForAdapterPosition(adapterPosition) as? BoardColumnViewHolder)
                        ?.also { columnVH ->
                            val view = columnVH.itemView
                            val initialWidth = view.globalVisibleRect.width()
                            val targetWidth = this.globalVisibleRect.width()
                            var scrollByRemaining = view.x
                            // Initialize width to be the actual width value instead of
                            // WRAP_CONTENT (-2) or MATCH_PARENT (-1)
                            // This should have no visible effect
                            view.updateLayoutParamsSafe {
                                width = initialWidth
                            }
                            // Start animation!
                            view.startAnimation(
                                    animation { interpolatedTime: Float, _ ->
                                        view.updateLayoutParamsSafe {
                                            if (view.x > 0) {
                                                // Internal Android Exception occurring below!
                                                // Use scrollByRemaining to fix this TODO
                                                scrollBy((view.x * interpolatedTime).roundToInt(), 0)
                                                view.x -= (view.x.F * interpolatedTime).roundToInt()
                                            }
                                            if (width < targetWidth)
                                                width += ((targetWidth - width).F * interpolatedTime).roundToInt()
                                        }
                                    }.also {
                                        it.interpolator = AccelerateInterpolator(2.0F)
                                        it.duration = 500L
                                        it.setAnimationListener(
                                                animationListener(
                                                        onStart = {
                                                            animationListener.onAnimationStart(it)
                                                        },
                                                        onRepeat = {
                                                            animationListener.onAnimationRepeat(it)
                                                        },
                                                        onEnd = {
                                                            animationListener.onAnimationEnd(it)
                                                            postDelayed(300) {
                                                                columnWidth = MATCH_PARENT
                                                                //scrollToPosition(adapterPosition)
                                                                allViewHolders
                                                                        .forEach {
                                                                            it.itemView
                                                                                    .updateLayoutParamsSafe { width = columnWidth }
                                                                        }
                                                                //val state = saveState()
                                                                //notifyAllItemsChanged()
                                                                //restoreFromState(state)
                                                                //isSnappingToItems = true
                                                            }
                                                        }
                                                )
                                        )
                                    }
                            )
                        }
            }
            // While or after doing that we need to let BoardView know that the width of each Column
            // is now different, try to update allViewHolders and also some global variable
        }
    }

    /**
     * The passed in [adapter] must be a descendant of [BoardAdapter].
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardAdapter) {
            super.setAdapter(adapter)
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
    fun saveState(): BoardViewSavedState {
        val savedState = BoardViewSavedState(super.onSaveInstanceState() as? RecyclerViewState)
        boardAdapter?.also { boardAdapter ->
            allViewHolders.forEach {
                (it as? BoardColumnViewHolder)?.also { holder ->
                    holder.list?.layoutManager?.saveState()?.also {
                        boardAdapter.layoutStates[holder.adapterPosition] = it
                    }
                }
            }
            savedState.layoutStates = boardAdapter.layoutStates.values.toList()
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
    fun restoreFromState(state: BoardViewSavedState) {
        super.onRestoreInstanceState(state.savedState)
        boardAdapter?.layoutStates?.also { hashMap ->
            state.layoutStates?.also {
                it.forEachIndexed { index, linearState ->
                    hashMap[index] = linearState
                }
            }
        }
    }

    @SuppressLint("MissingSuperCall") // we called super in saveState()
    override fun onSaveInstanceState(): Parcelable? = saveState()

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is BoardViewSavedState) {
            restoreFromState(state)
        } else super.onRestoreInstanceState(state)
    }
}

abstract class BoardAdapter(
        var adapter: BoardContainerAdapter? = null
) : BaseAdapter<BoardColumnViewHolder>() {

    // Ideally we wouldn't want this but we want to not keep a reference of the BoardView!
    internal var columnWidth: Int = WRAP_CONTENT

    /**
     * The layout states of each [BoardColumnViewHolder] with its adapter position as the key
     */
    internal val layoutStates = HashMap<Int, LinearState>()

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

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        super.writeToParcel(dest, flags)
        dest?.also { it.writeTypedList(layoutStates) }
    }

    companion object CREATOR : Parcelable.Creator<BoardViewSavedState> {

        override fun createFromParcel(parcel: Parcel): BoardViewSavedState =
                BoardViewSavedState(parcel.readParcelable(RecyclerViewState::class.java
                        .classLoader)).also {
                    val list = ArrayList<LinearState>()
                    parcel.readTypedList(list, LinearState.CREATOR)
                    it.layoutStates = list
                }

        override fun newArray(size: Int): Array<BoardViewSavedState?> = arrayOfNulls(size)
    }
}