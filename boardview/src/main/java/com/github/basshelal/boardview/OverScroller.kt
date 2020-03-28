@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import androidx.recyclerview.widget.RecyclerView
import me.everything.android.ui.overscroll.HorizontalOverScrollBounceEffectDecorator
import me.everything.android.ui.overscroll.IOverScrollState
import me.everything.android.ui.overscroll.VerticalOverScrollBounceEffectDecorator
import me.everything.android.ui.overscroll.adapters.RecyclerViewOverScrollDecorAdapter

const val overScrollMultiplier = 35.0

// TODO: 28-Mar-20 We should convert this into an independent component or library
//  and remove the dependency on the other old library

interface OverScroller {
    var isEnabled: Boolean
    val isOverScrolling: Boolean
    fun overScroll(amount: Number)
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        isEnabled = true
        recyclerView.addOnScrollListener(OnScrollListener(this))
    }

    fun detachFromRecyclerView(recyclerView: RecyclerView) {
        isEnabled = false
        recyclerView.removeOnScrollListener(OnScrollListener(this))
    }
}

class VerticalOverScroller(val recyclerView: RecyclerView) :
        VerticalOverScrollBounceEffectDecorator(
                RecyclerViewOverScrollDecorAdapter(recyclerView)), OverScroller {

    var isAttached: Boolean = true

    override var isEnabled: Boolean
        get() = this.isAttached
        set(value) {
            isAttached = value
            if (value) {
                this.attach()
            } else if (!isOverScrolling) {
                this.detach()
            }
        }

    override val isOverScrolling: Boolean
        get() = currentState != IOverScrollState.STATE_IDLE

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

class HorizontalOverScroller(val recyclerView: RecyclerView) :
        HorizontalOverScrollBounceEffectDecorator(
                RecyclerViewOverScrollDecorAdapter(recyclerView)), OverScroller {

    var isAttached: Boolean = true

    override var isEnabled: Boolean
        get() = this.isAttached
        set(value) {
            isAttached = value
            if (value) this.attach() else this.detach()
        }

    override val isOverScrolling: Boolean
        get() = currentState != IOverScrollState.STATE_IDLE

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

    override fun hashCode(): Int = 69420
}
