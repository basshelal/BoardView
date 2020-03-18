@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import me.everything.android.ui.overscroll.OverScrollBounceEffectDecoratorBase

/**
 * Base class for all [RecyclerView]s in this library for shared functionality
 */
abstract class BaseRecyclerView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    /**
     * All ***visible or bound*** [RecyclerView.ViewHolder]s in this [RecyclerView].
     *
     * This will usually include all visible ViewHolders as well as some invisible ViewHolders
     * that have been created, bound and cached by [RecyclerView].
     *
     * It is safe to assume that this will return all ViewHolders that this [RecyclerView] can
     * possibly return.
     */
    inline val allViewHolders: Sequence<ViewHolder>
        get() = children.map { getChildViewHolder(it) }

    internal var overScroller: OverScroller? = null
    var isOverScrollingEnabled: Boolean = true
        set(value) {
            field = value
            overScroller?.isEnabled = value
            toggleOverScroller(value)
        }

    var horizontalScrollSpeed: Double = 0.0
    var verticalScrollSpeed: Double = 0.0

    private var oldHorizontalScrollOffset: Int = 0
    private var oldVerticalScrollOffset: Int = 0
    private var oldTime: Long = now

    private val overScrollListener = onScrollListener { dx, dy ->
        // Take a snapshot of the stuff we will use so that it hasn't changed by the time we call
        // getters again, this is only for the stuff that is extremely volatile like time and
        // scrollOffset which change A LOT very frequently
        val dSecs = (now - oldTime).D * 1E-3
        val verticalOffSet = verticalScrollOffset
        val horizontalOffset = horizontalScrollOffset

        verticalScrollSpeed = (verticalOffSet.D - oldVerticalScrollOffset.D) / dSecs
        horizontalScrollSpeed = (horizontalOffset.D - oldHorizontalScrollOffset.D) / dSecs

        if (dy != 0 && (verticalOffSet == 0 || verticalOffSet == maxVerticalScroll)) {
            overScroller?.overScroll((verticalScrollSpeed * overScrollMultiplier) / height)
        }

        if (dx != 0 && (horizontalOffset == 0 || horizontalOffset == maxHorizontalScroll)) {
            overScroller?.overScroll((horizontalScrollSpeed * overScrollMultiplier) / width)
        }

        oldVerticalScrollOffset = verticalOffSet
        oldHorizontalScrollOffset = horizontalOffset
        oldTime = now
    }

    var overScrollStateChangeListener: (oldState: Int, newState: Int) -> Unit = { oldState, newState -> }
        set(value) {
            field = value
            (overScroller as? OverScrollBounceEffectDecoratorBase)
                    ?.setOverScrollStateListener { decor, oldState, newState ->
                        overScrollStateChangeListener(oldState, newState)
                    }
        }

    /**
     * You are not supposed to set the [LayoutManager] of BoardView's [RecyclerView]s, this is
     * managed internally!
     * You can however, change properties of the [LayoutManager] by calling [getLayoutManager].
     */
    override fun setLayoutManager(lm: LayoutManager?) {
        if (lm is SaveRestoreLinearLayoutManager) {
            super.setLayoutManager(lm)
            setUpOverScroller()
        } else if (lm != null)
            logE("You are not allowed to set the Layout Manager of ${this::class.qualifiedName}\n" +
                    "Passed in ${lm::class.qualifiedName}")
    }

    /**
     * Because BoardView's [LayoutManager] is managed internally, it is guaranteed to be a [SaveRestoreLinearLayoutManager]
     */
    override fun getLayoutManager(): SaveRestoreLinearLayoutManager? =
            super.getLayoutManager() as? SaveRestoreLinearLayoutManager

    /**
     * Calls [RecyclerView.Adapter.notifyItemChanged] on each [RecyclerView.ViewHolder] in
     * [allViewHolders].
     * These are the already bound ViewHolders in this [RecyclerView].
     */
    inline fun notifyAllItemsChanged() {
        allViewHolders.forEach { adapter?.notifyItemChanged(it.adapterPosition) }
    }

    private inline fun setUpOverScroller() {
        overScroller = if (layoutManager?.orientation == LinearLayoutManager.VERTICAL)
            VerticalOverScroller(this) else HorizontalOverScroller(this)

        isScrollbarFadingEnabled = true
        scrollBarFadeDuration = 500
        toggleOverScroller(isOverScrollingEnabled)
    }

    private fun toggleOverScroller(enable: Boolean) {
        if (enable) {
            overScrollMode = View.OVER_SCROLL_NEVER
            addOnScrollListener(overScrollListener)
        } else {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            removeOnScrollListener(overScrollListener)
        }
    }
}

/**
 * Base class for all [RecyclerView.Adapter]s in this library for shared functionality
 */
abstract class BaseAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    init {
        this.setHasStableIds(true)
    }

    abstract override fun getItemId(position: Int): Long
}

/**
 * Base class for all [RecyclerView.ViewHolder]s in this library for shared functionality
 */
abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

typealias LinearState = LinearLayoutManager.SavedState

/**
 * [LinearLayoutManager] that can save and restore state correctly
 */
open class SaveRestoreLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    var savedState: LinearState? = null
        internal set

    open fun saveState(): LinearState? {
        savedState = onSaveInstanceState() as? LinearState
        return savedState
    }

    open fun restoreState(state: LinearState? = savedState): LinearState? {
        onRestoreInstanceState(state)
        return state
    }

    override fun supportsPredictiveItemAnimations(): Boolean = false
}

open class BoardViewItemAnimator : SimpleItemAnimator() {

    val duration = 150L

    override fun getAddDuration(): Long {
        TODO("not implemented")
    }

    override fun isRunning(): Boolean {
        TODO("not implemented")
    }

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        TODO("not implemented")
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder?): Boolean {
        TODO("not implemented")
    }

    override fun getChangeDuration(): Long {
        TODO("not implemented")
    }

    override fun endAnimations() {
        TODO("not implemented")
    }

    override fun getMoveDuration(): Long {
        TODO("not implemented")
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder?): Boolean {
        TODO("not implemented")
    }

    override fun runPendingAnimations() {
        TODO("not implemented")
    }

    override fun getRemoveDuration(): Long {
        TODO("not implemented")
    }

    override fun animateMove(holder: RecyclerView.ViewHolder?,
                             fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        TODO("not implemented")
    }

    override fun animateChange(oldHolder: RecyclerView.ViewHolder?, newHolder: RecyclerView.ViewHolder?,
                               fromLeft: Int, fromTop: Int, toLeft: Int, toTop: Int): Boolean {
        TODO("not implemented")
    }
}