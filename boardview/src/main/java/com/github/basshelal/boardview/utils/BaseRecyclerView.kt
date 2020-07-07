@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview.utils

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Base class for all [RecyclerView]s in this library for shared functionality
 *
 * All [RecyclerView]s in this library use a [SaveRestoreLinearLayoutManager] for their
 * LayoutManager as well coming with over-scrolling functionality out of the box which can be
 * enables or disabled using [isOverScrollingEnabled]
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

    /** Shorthand for [SaveRestoreLinearLayoutManager.isScrollEnabled] */
    inline var isScrollEnabled: Boolean
        set(value) {
            layoutManager?.isScrollEnabled = value
        }
        get() = layoutManager?.isScrollEnabled ?: true

    /**
     * Used to change the over-scrolling behavior of this [RecyclerView].
     *
     * Over-scrolling happens when the user scrolls past the bounds either on purpose or if
     * scrolling too quickly and reached the end of scroll bounds. Stock Android behavior in both
     * cases is  a glowing effect, but using an over-scroller allows the appearance to be more
     * realistic and resemble iOS scrolling behavior.
     */
    inline var isOverScrollingEnabled: Boolean
        get() = overScroller?.isAttached ?: false
        set(value) {
            if (value) overScroller?.attachToRecyclerView(this)
            else overScroller?.detachFromRecyclerView(this)
        }

    @PublishedApi
    internal var overScroller: OverScroller? = null

    /**
     * You are not supposed to set the [LayoutManager]s of this library's [RecyclerView]s, this is
     * managed internally!
     *
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

        isOverScrollingEnabled = true

        isScrollbarFadingEnabled = true
        scrollBarFadeDuration = 500
    }
}

/**
 * Base class for all [RecyclerView.Adapter]s in this library for shared functionality.
 *
 * All adapters must use stable ids by correctly overriding [getItemId]
 */
abstract class BaseAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    init {
        this.setHasStableIds(true)
    }

    /* inheritDoc */
    abstract override fun getItemId(position: Int): Long
}

/**
 * Base class for all [RecyclerView.ViewHolder]s in this library for shared functionality
 */
abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

typealias LinearState = LinearLayoutManager.SavedState

/**
 * A [LinearLayoutManager] that can save and restore state correctly as well as easily enable and
 * disable scrolling through [isScrollEnabled].
 *
 * There is no reason that you should ever need to extend this class as it changes very little
 * anyway from [LinearLayoutManager], however it is still left open for convenience.
 */
open class SaveRestoreLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    /**
     * The internally managed [LinearLayoutManager.SavedState] of this [LinearLayoutManager].
     *
     * This is saved in [saveState] and is the default state used in [restoreState]
     */
    protected var savedState: LinearState? = null

    /**
     * Used to easily enable or disable scrolling
     *
     * @see canScrollVertically
     * @see canScrollHorizontally
     */
    var isScrollEnabled: Boolean = true

    /**
     * Used to easily enable or disable predictive item animations
     *
     * @see supportsPredictiveItemAnimations
     */
    var supportsPredictiveItemAnimations: Boolean = true

    /**
     * Used to save the state of this [LinearLayoutManager] which can then be restored in
     * [restoreState].
     *
     * @return the [LinearLayoutManager.SavedState] of this [LinearLayoutManager], ideally you
     * will not need this as it kept internally and can be restored in [restoreState], however
     * there are scenarios where that may be useful but use caution
     */
    open fun saveState(): LinearState? {
        savedState = onSaveInstanceState() as? LinearState
        return savedState
    }

    /**
     * Used to restore the state of this [LinearLayoutManager] which can be saved in [saveState].
     *
     * @param state the [LinearLayoutManager.SavedState] to restore this [LinearLayoutManager]
     * to, ideally you will not need to provide this as it is kept internally by calling
     * [saveState], however there are scenarios where providing your own state may be useful,
     * however use caution
     */
    open fun restoreState(state: LinearState? = savedState): LinearState? {
        onRestoreInstanceState(state)
        return state
    }

    override fun canScrollVertically(): Boolean = super.canScrollVertically() && isScrollEnabled

    override fun canScrollHorizontally(): Boolean = super.canScrollHorizontally() && isScrollEnabled

    override fun supportsPredictiveItemAnimations(): Boolean = supportsPredictiveItemAnimations
}