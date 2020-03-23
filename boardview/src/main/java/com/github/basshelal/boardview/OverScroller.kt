@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import androidx.recyclerview.widget.RecyclerView
import me.everything.android.ui.overscroll.HorizontalOverScrollBounceEffectDecorator
import me.everything.android.ui.overscroll.IOverScrollState
import me.everything.android.ui.overscroll.VerticalOverScrollBounceEffectDecorator
import me.everything.android.ui.overscroll.adapters.RecyclerViewOverScrollDecorAdapter

const val overScrollMultiplier = 35.0

interface OverScroller {
    var isEnabled: Boolean
    val isOverScrolling: Boolean
    fun overScroll(amount: Number)
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