package com.github.basshelal.boardview

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.contains
import androidx.core.view.updateLayoutParams
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
}

abstract class BoardAdapter(
        var adapter: BoardContainerAdapter? = null
) : BaseAdapter<BoardViewColumnVH>() {

    val adapters = HashSet<BoardListAdapter<*>>()
    val layoutStates = HashMap<Int, LinearState>()

    // We handle the creation because we return BoardVH that contains columns
    // We have to do this ourselves because we resolve the header, footer and list layout as well
    // as managing list adapters
    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewColumnVH {
        val view = View.inflate(parent.context, R.layout.view_boardcolumn, null) as ConstraintLayout
        val viewHolder = BoardViewColumnVH(view).also { vh ->
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
    open fun onViewHolderCreated(holder: BoardViewColumnVH) {}

    final override fun onBindViewHolder(holder: BoardViewColumnVH, position: Int) {
        holder.list?.adapter.also { current ->
            if (current == null) {
                adapter?.onCreateListAdapter(position)?.also {
                    adapters.add(it)
                    holder.list?.adapter = it
                }
            } else {
                holder.boardListAdapter?.bindAdapter(holder, position)
                holder.list?.rebindAll()
            }
        }
        onViewHolderBound(holder, position)
    }

    // callback for caller to do stuff after onBindViewHolder is called
    open fun onViewHolderBound(holder: BoardViewColumnVH, position: Int) {}

    @CallSuper
    override fun onViewAttachedToWindow(holder: BoardViewColumnVH) {
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
    override fun onViewDetachedFromWindow(holder: BoardViewColumnVH) {
        holder.list?.layoutManager?.saveState()?.also {
            layoutStates[holder.adapterPosition] = it
        }
    }
}

/**
 * ViewHolder for the [BoardColumn], these are used in [BoardView] and its adapter [BoardAdapter]
 */
open class BoardViewColumnVH(itemView: View) : BaseViewHolder(itemView) {
    var header: View? = null
        internal set
    var list: BoardList? = null
        internal set
    var footer: View? = null
        internal set
    inline val boardListAdapter: BoardListAdapter<*>? get() = list?.adapter as? BoardListAdapter
}