package com.github.basshelal.boardview

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.contains
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The list that displays ItemViews, this is nothing different from an ordinary RecyclerView
 */
class BoardList
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    inline val boardListAdapter: BoardListAdapter<*>? get() = this.adapter as? BoardListAdapter

    // Vertical Scrolling info
    private val interpolator = LogarithmicInterpolator()
    private val updateRatePerMilli = floor(millisPerFrame)
    private val maxScrollBy = (updateRatePerMilli * 1.5F).roundToInt()
    private var verticalScrollBoundWidth = 0F
    private val outsideTopScrollBounds = RectF()
    private val topScrollBounds = RectF()
    private val outsideBottomScrollBounds = RectF()
    private val bottomScrollBounds = RectF()

    init {
        setRecycledViewPool(BoardViewContainer.ITEM_VH_POOL)
        layoutManager = SaveRestoreLinearLayoutManager(context).also {
            it.orientation = VERTICAL
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 6
        }
        viewTreeObserver.addOnScrollChangedListener {
            resetScrollInfo()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        resetScrollInfo()
    }

    private fun resetScrollInfo() {
        doInBackground {
            verticalScrollBoundWidth = this.globalVisibleRectF.height() / 10F
            outsideTopScrollBounds.set(this.globalVisibleRectF.also {
                it.bottom = it.top
                it.top = 0F
            })
            topScrollBounds.set(this.globalVisibleRectF.also {
                it.bottom = it.top + verticalScrollBoundWidth
            })
            outsideBottomScrollBounds.set(this.globalVisibleRectF.also {
                it.top = it.bottom
                it.bottom = realScreenHeight.F
            })
            bottomScrollBounds.set(this.globalVisibleRectF.also {
                it.top = it.bottom - verticalScrollBoundWidth
            })
        }
    }

    /**
     * The passed in [adapter] must be a descendant of [BoardListAdapter].
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardListAdapter) super.setAdapter(adapter) else if (adapter != null)
            logE("BoardList adapter must be a descendant of BoardListAdapter!\n" +
                    "passed in adapter is of type ${adapter::class.simpleName}")
    }


    fun verticalScroll(touchPoint: PointF) {
        var scrollBy = 0
        when (touchPoint) {
            in topScrollBounds -> {
                val multiplier = interpolator[
                        1F - (touchPoint.y - topScrollBounds.top) / (topScrollBounds.bottom - topScrollBounds.top)]
                scrollBy = -(maxScrollBy * multiplier).roundToInt()
            }
            in bottomScrollBounds -> {
                val multiplier = interpolator[
                        (touchPoint.y - bottomScrollBounds.top) / (bottomScrollBounds.bottom - bottomScrollBounds.top)]
                scrollBy = (maxScrollBy * multiplier).roundToInt()
            }
            in outsideTopScrollBounds -> scrollBy = -maxScrollBy
            in outsideBottomScrollBounds -> scrollBy = maxScrollBy
        }
        this.scrollBy(0, scrollBy)
    }

}

/**
 * The adapter responsible for displaying ItemViews, this is nothing different from an ordinary
 * Adapter.
 *
 * [BoardView] recycles the [BoardListAdapter]s it uses for performance reasons, hence, you must
 * override [bindAdapter] to inform [BoardView] how to properly bind an adapter to the position.
 */
abstract class BoardListAdapter<VH : BoardItemViewHolder>(
        var adapter: BoardContainerAdapter? = null
) : BaseAdapter<VH>() {

    /**
     * Override to inform [BoardAdapter] how to bind this adapter to the given [position] and
     * [holder]. This is called in [BoardAdapter]'s [onBindViewHolder].
     *
     * Typically, callers will rebind or re-set their data sets here in order to ensure that this
     * [BoardListAdapter] can be used correctly by the passed in [holder]'s
     * [BoardColumnViewHolder.list]
     *
     * This is needed because [BoardView] recycles its adapters as well as its Views to increase
     * performance and reduce the memory overhead incurred when using nested [RecyclerView]s
     */
    abstract fun bindAdapter(holder: BoardColumnViewHolder, position: Int)
}

/**
 * Contains the ItemView in each list, these are like cards
 * These are used in [BoardList] and its adapter [BoardListAdapter] but are also accessible by
 * [BoardView] and its adapter [BoardAdapter] because they are a part of [BoardColumnViewHolder]
 */
open class BoardItemViewHolder(itemView: View) : BaseViewHolder(itemView)