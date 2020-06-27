@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview.utils

import androidx.recyclerview.widget.RecyclerView
import me.everything.android.ui.overscroll.HorizontalOverScrollBounceEffectDecorator
import me.everything.android.ui.overscroll.IOverScrollState
import me.everything.android.ui.overscroll.VerticalOverScrollBounceEffectDecorator
import me.everything.android.ui.overscroll.adapters.RecyclerViewOverScrollDecorAdapter

// TODO: 27-Jun-20 Everything here is in need of cleanup and organization!
//  For now it works well (which makes me think why fix it anyway lol), so we'll keep it

internal const val overScrollMultiplier = 35.0

@Beta
@PublishedApi
internal interface OverScroller {
    var isAttached: Boolean
    val isOverScrolling: Boolean
    fun overScroll(amount: Number)
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(OnScrollListener(this))
        isAttached = true
    }

    fun detachFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnScrollListener(OnScrollListener(this))
        isAttached = false
    }
}

@Beta
internal class VerticalOverScroller(val recyclerView: RecyclerView) :
        VerticalOverScrollBounceEffectDecorator(
                RecyclerViewOverScrollDecorAdapter(recyclerView)), OverScroller {

    override var isAttached: Boolean = true
        set(value) {
            field = value
        }
        get() = field

    override val isOverScrolling: Boolean
        get() = currentState != IOverScrollState.STATE_IDLE

    override fun attachToRecyclerView(recyclerView: RecyclerView) {
        super.attachToRecyclerView(recyclerView)
        this.attach()
    }

    override fun detachFromRecyclerView(recyclerView: RecyclerView) {
        super.detachFromRecyclerView(recyclerView)
        this.detach()
    }

    override fun overScroll(amount: Number) {
        // bound the input so that extreme numbers are limited
        val limit = recyclerView.height.F / 2F
        var actual = amount.F
        if (amount.F < -limit && amount.F < 0) actual = -limit
        if (amount.F > limit && amount.F > 0) actual = limit
        issueStateTransition(mOverScrollingState)
        translateView(recyclerView, -actual)
        issueStateTransition(mBounceBackState)
    }
}

@Beta
internal class HorizontalOverScroller(val recyclerView: RecyclerView) :
        HorizontalOverScrollBounceEffectDecorator(
                RecyclerViewOverScrollDecorAdapter(recyclerView)), OverScroller {

    override var isAttached: Boolean = true
        set(value) {
            field = value
        }
        get() = field

    override val isOverScrolling: Boolean
        get() = currentState != IOverScrollState.STATE_IDLE

    override fun attachToRecyclerView(recyclerView: RecyclerView) {
        super.attachToRecyclerView(recyclerView)
        this.attach()
    }

    override fun detachFromRecyclerView(recyclerView: RecyclerView) {
        super.detachFromRecyclerView(recyclerView)
        this.detach()
    }

    override fun overScroll(amount: Number) {
        // bound the input so that extreme numbers are limited
        val limit = recyclerView.width.F / 2F
        var actual = amount.F
        if (amount.F < -limit && amount.F < 0) actual = -limit
        if (amount.F > limit && amount.F > 0) actual = limit
        issueStateTransition(mOverScrollingState)
        translateView(recyclerView, -actual)
        issueStateTransition(mBounceBackState)
    }

}

private class OnScrollListener(val overScroller: OverScroller) : RecyclerView.OnScrollListener() {

    private var horizontalScrollSpeed: Double = 0.0
    private var verticalScrollSpeed: Double = 0.0

    private var oldHorizontalScrollOffset: Int = 0
    private var oldVerticalScrollOffset: Int = 0
    private var oldTime: Long = now

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        // Take a snapshot of the stuff we will use so that it hasn't changed by the time we call
        // getters again, this is only for the stuff that is extremely volatile like time and
        // scrollOffset which change A LOT very frequently
        val dSecs = (now - oldTime).D * 1E-3
        val verticalOffSet = recyclerView.verticalScrollOffset
        val horizontalOffset = recyclerView.horizontalScrollOffset

        verticalScrollSpeed = (verticalOffSet.D - oldVerticalScrollOffset.D) / dSecs
        horizontalScrollSpeed = (horizontalOffset.D - oldHorizontalScrollOffset.D) / dSecs

        if (dy != 0 && (verticalOffSet == 0 || verticalOffSet == recyclerView.maxVerticalScroll)) {
            overScroller.overScroll((verticalScrollSpeed * overScrollMultiplier) / recyclerView.height)
        }

        if (dx != 0 && (horizontalOffset == 0 || horizontalOffset == recyclerView.maxHorizontalScroll)) {
            overScroller.overScroll((horizontalScrollSpeed * overScrollMultiplier) / recyclerView.width)
        }

        oldVerticalScrollOffset = verticalOffSet
        oldHorizontalScrollOffset = horizontalOffset
        oldTime = now
    }

    override fun equals(other: Any?): Boolean = other is OnScrollListener

    override fun hashCode(): Int = 69420 // nice...
}
