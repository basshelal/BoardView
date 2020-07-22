@file:Suppress("RedundantVisibilityModifier", "NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.FloatRange
import androidx.core.graphics.contains
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.BOTTOM
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.BOTTOM_LEFT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.BOTTOM_RIGHT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.DOWN_INSIDE
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.DOWN_INSIDE_LEFT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.DOWN_INSIDE_RIGHT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.ERROR
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.INSIDE
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.LEFT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.RIGHT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.TOP
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.TOP_LEFT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.TOP_RIGHT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.UP_INSIDE
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.UP_INSIDE_LEFT
import com.github.basshelal.boardview.BoardList.BoardListBounds.Sector.UP_INSIDE_RIGHT
import com.github.basshelal.boardview.utils.BaseAdapter
import com.github.basshelal.boardview.utils.BaseRecyclerView
import com.github.basshelal.boardview.utils.BaseViewHolder
import com.github.basshelal.boardview.utils.I
import com.github.basshelal.boardview.utils.LogarithmicInterpolator
import com.github.basshelal.boardview.utils.SaveRestoreLinearLayoutManager
import com.github.basshelal.boardview.utils.canScrollVertically
import com.github.basshelal.boardview.utils.copy
import com.github.basshelal.boardview.utils.findChildViewUnderRaw
import com.github.basshelal.boardview.utils.firstVisibleViewHolder
import com.github.basshelal.boardview.utils.get
import com.github.basshelal.boardview.utils.globalVisibleRectF
import com.github.basshelal.boardview.utils.lastVisibleViewHolder
import com.github.basshelal.boardview.utils.logE
import com.github.basshelal.boardview.utils.millisPerFrame
import com.github.basshelal.boardview.utils.show
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The list that displays ItemViews, this is nothing different from an ordinary RecyclerView
 */
class BoardList
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    /**
     * A multiplier to modify the rate of vertical scrolling only
     * when the user is dragging either an item.
     *
     * A value less than 1F is used to slow down the scrolling rate,
     * a value greater than 1F is used to increase the scrolling rate,
     * values less than 0F are not allowed.
     *
     * This is only used internally once in [verticalScroll] which is the function responsible
     * for scrolling when the user is dragging an item
     */
    @FloatRange(from = 0.0)
    public var dragScrollMultiplier = 1.0F
        set(value) {
            if (value < 0.0F)
                throw IllegalArgumentException("Drag Scroll Multiplier must be at least 0.0F," +
                        " passed in $value")
            else field = value
        }

    inline val boardListAdapter: BoardListAdapter<*>? get() = this.adapter as? BoardListAdapter
    inline val boardListItemAnimator: BoardListItemAnimator?
        get() = this.itemAnimator as? BoardListItemAnimator

    // Vertical Scrolling info, transient shit
    private val interpolator = LogarithmicInterpolator()
    private val updateRatePerMilli = floor(millisPerFrame)
    private val maxScrollBy = (updateRatePerMilli * 1.5F).roundToInt()
    private val bounds = BoardListBounds(globalVisibleRectF)

    init {
        this.setHasFixedSize(true)
        setRecycledViewPool(BoardViewContainer.ITEM_VH_POOL)
        layoutManager = SaveRestoreLinearLayoutManager(context).also {
            it.orientation = VERTICAL
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 6
        }
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = true
        viewTreeObserver.addOnScrollChangedListener { bounds.set(this.globalVisibleRectF) }
        itemAnimator = BoardListItemAnimator().also { it.duration = 100 }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        bounds.set(this.globalVisibleRectF)
    }

    /**
     * The passed in [adapter] must be a descendant of [BoardListAdapter].
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardListAdapter)
            super.setAdapter(adapter)
        else if (adapter != null)
            logE("BoardList adapter must be a descendant of BoardListAdapter!\n" +
                    "passed in adapter is of type ${adapter::class.simpleName}")
    }

    internal fun verticalScroll(touchPoint: PointF) {
        val scrollBy: Int
        when (bounds.findSectorForPoint(touchPoint)) {
            UP_INSIDE, UP_INSIDE_LEFT, UP_INSIDE_RIGHT -> {
                val multiplier = interpolator[
                        1F - (touchPoint.y - bounds.scrollUp.top) /
                                (bounds.scrollUp.bottom - bounds.scrollUp.top)]
                scrollBy = -(maxScrollBy * multiplier).roundToInt()
            }
            DOWN_INSIDE, DOWN_INSIDE_LEFT, DOWN_INSIDE_RIGHT -> {
                val multiplier = interpolator[
                        (touchPoint.y - bounds.scrollDown.top) /
                                (bounds.scrollDown.bottom - bounds.scrollDown.top)]
                scrollBy = (maxScrollBy * multiplier).roundToInt()
            }
            TOP, TOP_LEFT, TOP_RIGHT -> scrollBy = -maxScrollBy
            BOTTOM, BOTTOM_LEFT, BOTTOM_RIGHT -> scrollBy = maxScrollBy
            else -> return
        }
        this.scrollBy(0, (scrollBy * dragScrollMultiplier).I)
    }

    private inline fun viewHolderUnderRaw(pointF: PointF): BoardItemViewHolder? {
        return findChildViewUnderRaw(pointF)?.let { getChildViewHolder(it) as? BoardItemViewHolder }
    }

    internal fun getViewHolderUnder(point: PointF): BoardItemViewHolder? {
        return when (bounds.findSectorForPoint(point)) {
            TOP, TOP_LEFT, TOP_RIGHT -> when (layoutManager?.reverseLayout) {
                false -> firstVisibleViewHolder as? BoardItemViewHolder
                true -> lastVisibleViewHolder as? BoardItemViewHolder
                else -> null
            }
            BOTTOM, BOTTOM_LEFT, BOTTOM_RIGHT -> when (layoutManager?.reverseLayout) {
                false -> lastVisibleViewHolder as? BoardItemViewHolder
                true -> firstVisibleViewHolder as? BoardItemViewHolder
                else -> null
            }
            LEFT, UP_INSIDE_LEFT, DOWN_INSIDE_LEFT ->
                viewHolderUnderRaw(point.copy { x = this@BoardList.globalVisibleRectF.left + 1 })
            RIGHT, UP_INSIDE_RIGHT, DOWN_INSIDE_RIGHT ->
                viewHolderUnderRaw(point.copy { x = this@BoardList.globalVisibleRectF.right - 1 })
            else -> viewHolderUnderRaw(point)
        }
    }

    /* Weird shit happens whenever we do a swap with an item at layout position 0,
     * This is because of how LinearLayoutManager works, it ends up scrolling for us even
     * though we never told it to, see more here
     * https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
     * So we solve this by forcing it back where it was, essentially cancelling the
     * scroll it did
     * This is fully done for us in LinearLayoutManager.prepareForDrop()
     */
    internal inline fun prepareForDrop(draggingVH: BoardItemViewHolder, targetVH: BoardItemViewHolder?) {
        if (targetVH != null && canScrollVertically &&
                (draggingVH.layoutPosition == 0 || targetVH.layoutPosition == 0 ||
                        this[0] == draggingVH.itemView || this[0] == targetVH.itemView)) {
            boardListItemAnimator?.prepareForDrop()
            layoutManager?.prepareForDrop(draggingVH.itemView, targetVH.itemView, 0, 0)
        }
    }

    /** Used to manage bounds for scrolling and swapping */
    private class BoardListBounds(globalRectF: RectF) {

        var verticalScrollBoundsHeight = globalRectF.height() / 10F

        // Rectangles
        val inside = globalRectF
        val scrollUp = RectF()
        val scrollDown = RectF()
        val top = RectF()
        val bottom = RectF()
        val left = RectF()
        val right = RectF()

        init {
            set(globalRectF)
        }

        inline fun set(globalRectF: RectF) {
            verticalScrollBoundsHeight = globalRectF.height() / 10F
            inside.set(globalRectF)
            scrollUp.set(globalRectF.copy {
                bottom = top + verticalScrollBoundsHeight
                left = 0F
                right = Float.MAX_VALUE
            })
            scrollDown.set(globalRectF.copy {
                top = bottom - verticalScrollBoundsHeight
                left = 0F
                right = Float.MAX_VALUE
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

        // For debugging to see the bounds
        inline fun showAll(view: View) {
            inside.show(view, Color.BLACK)
            scrollUp.show(view, Color.BLUE)
            scrollDown.show(view, Color.RED)
            left.show(view, Color.CYAN)
            right.show(view, Color.MAGENTA)
            top.show(view, Color.GREEN)
            bottom.show(view, Color.YELLOW)
        }

        inline fun findSectorForPoint(point: PointF): Sector {
            return when (point) {
                in top -> when (point) {
                    in left -> TOP_LEFT
                    in right -> TOP_RIGHT
                    else -> TOP
                }
                in scrollUp -> when (point) {
                    in left -> UP_INSIDE_LEFT
                    in right -> UP_INSIDE_RIGHT
                    else -> UP_INSIDE
                }
                in scrollDown -> when (point) {
                    in left -> DOWN_INSIDE_LEFT
                    in right -> DOWN_INSIDE_RIGHT
                    else -> DOWN_INSIDE
                }
                in bottom -> when (point) {
                    in left -> BOTTOM_LEFT
                    in right -> BOTTOM_RIGHT
                    else -> BOTTOM
                }
                in left -> LEFT
                in right -> RIGHT
                in inside -> INSIDE
                else -> ERROR
            }
        }

        enum class Sector {
            TOP_LEFT, TOP, TOP_RIGHT,
            UP_INSIDE_LEFT, UP_INSIDE, UP_INSIDE_RIGHT,
            LEFT, INSIDE, RIGHT,
            DOWN_INSIDE_LEFT, DOWN_INSIDE, DOWN_INSIDE_RIGHT,
            BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT,
            ERROR
        }
    }
}

/**
 * The adapter responsible for displaying ItemViews, this is nothing different from an ordinary
 * Adapter.
 *
 * [BoardView] recycles the [BoardListAdapter]s it uses for performance reasons, hence, you must
 * override [bindAdapter] to inform [BoardView] how to properly bind an adapter to the position.
 */
abstract class BoardListAdapter<VH : BoardItemViewHolder> : BaseAdapter<VH>() {

    /**
     * Override to inform [BoardAdapter] how to bind this adapter to the given [position] and
     * [holder]. This is called in [BoardAdapter]'s [onBindViewHolder]. If an adapter doesn't
     * exist in the [position] then [BoardContainerAdapter.onCreateListAdapter] is called.
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