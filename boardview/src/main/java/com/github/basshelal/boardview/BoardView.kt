package com.github.basshelal.boardview

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.AbsSavedState
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.contains
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_boardcolumn.view.*
import kotlin.math.floor
import kotlin.math.roundToInt

open class BoardView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    inline val boardAdapter: BoardAdapter? get() = this.adapter as? BoardAdapter

    // Horizontal Scrolling info
    private val interpolator = LogarithmicInterpolator()
    private val updateRatePerMilli = floor(millisPerFrame)
    private val horizontalMaxScrollBy = (updateRatePerMilli * 2F).roundToInt()
    private var horizontalScrollBoundWidth = 0F
    private val outsideLeftScrollBounds = RectF()
    private val leftScrollBounds = RectF()
    private val outsideRightScrollBounds = RectF()
    private val rightScrollBounds = RectF()

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
        when {
            touchPoint in leftScrollBounds -> {
                val mult = interpolator[
                        1F - (touchPoint.x - leftScrollBounds.left) / (leftScrollBounds.right - leftScrollBounds.left)]
                scrollBy = -(horizontalMaxScrollBy * mult).roundToInt()
            }
            touchPoint in rightScrollBounds -> {
                val mult = interpolator[
                        (touchPoint.x - rightScrollBounds.left) / (rightScrollBounds.right - rightScrollBounds.left)]
                scrollBy = (horizontalMaxScrollBy * mult).roundToInt()
            }
            touchPoint in outsideLeftScrollBounds -> scrollBy = -horizontalMaxScrollBy
            touchPoint in outsideRightScrollBounds -> scrollBy = horizontalMaxScrollBy
        }
        this.scrollBy(scrollBy, 0)
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

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState() as? RecyclerViewState
        val savedState = BoardViewSavedState(superState)
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

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is BoardViewSavedState) {
            super.onRestoreInstanceState(state.savedState)
            boardAdapter?.layoutStates?.also { hashMap ->
                state.layoutStates?.also {
                    it.forEachIndexed { index, linearState ->
                        hashMap[index] = linearState
                    }
                }
            }
        } else super.onRestoreInstanceState(state)
    }
}

abstract class BoardAdapter(
        var adapter: BoardContainerAdapter? = null
) : BaseAdapter<BoardColumnViewHolder>() {

    val adapters = HashSet<BoardListAdapter<*>>()
    val layoutStates = HashMap<Int, LinearState>()

    // We handle the creation because we return BoardVH that contains columns
    // We have to do this ourselves because we resolve the header, footer and list layout as well
    // as managing list adapters
    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardColumnViewHolder {
        val view = View.inflate(parent.context, R.layout.view_boardcolumn, null) as ConstraintLayout
        val viewHolder = BoardColumnViewHolder(view).also { vh ->
            vh.list = vh.itemView.boardListView
        }
        // Header
        adapter?.onCreateListHeader(view)?.also {
            view.header_frameLayout.addView(it)
            viewHolder.header = it
            viewHolder.list?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = view.header_frameLayout.id
                height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            }
        }
        // Footer
        adapter?.onCreateFooter(view)?.also {
            view.footer_frameLayout.addView(it)
            viewHolder.footer = it
            viewHolder.list?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                // bottomToTop = view.footer_frameLayout.id
                // height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            }
        }
        onViewHolderCreated(viewHolder)
        return viewHolder
    }

    // callback for caller to do stuff after onCreateViewHolder is called
    open fun onViewHolderCreated(holder: BoardColumnViewHolder) {}

    final override fun onBindViewHolder(holder: BoardColumnViewHolder, position: Int) {
        holder.list?.adapter.also { current ->
            if (current == null) {
                adapter?.onCreateListAdapter(position)?.also {
                    adapters.add(it)
                    holder.list?.adapter = it
                }
            } else {
                holder.boardListAdapter?.bindAdapter(holder, position)
                holder.list?.notifyAllItemsChanged()
            }
        }
        onViewHolderBound(holder, position)
    }

    // callback for caller to do stuff after onBindViewHolder is called
    open fun onViewHolderBound(holder: BoardColumnViewHolder, position: Int) {}

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

typealias RecyclerViewState = RecyclerView.SavedState

class BoardViewSavedState(val savedState: RecyclerViewState?) : AbsSavedState(savedState) {

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