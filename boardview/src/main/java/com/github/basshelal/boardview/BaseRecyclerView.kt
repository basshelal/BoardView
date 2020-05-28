@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
     * This will usually include all visible ViewHolders as well possibly some invisible
     * ViewHolders that have been created, bound and attached to [RecyclerView].
     *
     * It is safe to assume that this will return all visible ViewHolders of this [RecyclerView].
     */
    inline val allVisibleViewHolders: Sequence<ViewHolder>
        get() = children.map { getChildViewHolder(it) }

    inline var isScrollEnabled: Boolean
        set(value) {
            layoutManager?.isScrollEnabled = value
        }
        get() = layoutManager?.isScrollEnabled ?: true

    internal var overScroller: OverScroller? = null
    var isOverScrollingEnabled: Boolean = true
        set(value) {
            field = value
            overScroller?.isEnabled = value
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
     * [allVisibleViewHolders].
     * These are the already bound ViewHolders in this [RecyclerView].
     */
    inline fun notifyAllItemsChanged() {
        allVisibleViewHolders.forEach { adapter?.notifyItemChanged(it.adapterPosition) }
    }

    private inline fun setUpOverScroller() {
        overScroller = if (layoutManager?.orientation == LinearLayoutManager.VERTICAL)
            VerticalOverScroller(this) else HorizontalOverScroller(this)

        overScroller?.attachToRecyclerView(this)

        isScrollbarFadingEnabled = true
        scrollBarFadeDuration = 500
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

    var isScrollEnabled: Boolean = true

    open fun saveState(): LinearState? {
        savedState = onSaveInstanceState() as? LinearState
        return savedState
    }

    open fun restoreState(state: LinearState? = savedState): LinearState? {
        onRestoreInstanceState(state)
        return state
    }

    override fun canScrollVertically(): Boolean = super.canScrollVertically() && isScrollEnabled

    override fun canScrollHorizontally(): Boolean = super.canScrollHorizontally() && isScrollEnabled

    override fun supportsPredictiveItemAnimations(): Boolean = true
}