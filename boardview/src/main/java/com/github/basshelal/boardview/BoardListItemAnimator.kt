@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

/* TODO: 28-May-20 ItemAnimator
 *  What do we need? We need an ItemAnimator that is 2 things:
 *  * Aware of ViewHolder swaps so that the animate appearance uses a 0F alpha
 *  * Applies animations as soon as they come instead of bulking them so that we get the effect that
 *    ItemTouchHelper does which is, as soon as we are over a ViewHolder begin animating, and
 *    have animations run in parallel
 */

open class BoardListItemAnimator : BaseItemAnimator() {

    fun prepareForDrop() {
        // hacky? we just ignore the pending removals because we know (or at least trust)
        // that they're incorrect
        onRunPendingAnimations = { pendingRemovals.clear() }
    }

    override fun obtainHolderInfo(): BoardItemHolderInfo {
        return BoardItemHolderInfo()
    }

    // struct we can use to contain additional information about a VH
    // that may be useful for animations
    class BoardItemHolderInfo : ItemHolderInfo() {

    }
}