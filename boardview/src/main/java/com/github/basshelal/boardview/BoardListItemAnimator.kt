@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import androidx.core.view.postOnAnimationDelayed
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.SimpleItemAnimator
import com.github.basshelal.boardview.utils.LogarithmicInterpolator
import com.github.basshelal.boardview.utils.reversedForEachIndexed
import org.jetbrains.anko.collections.forEachReversedByIndex
import org.jetbrains.anko.collections.forEachReversedWithIndex
import java.util.ArrayList
import kotlin.math.max

/**
 * A [RecyclerView.ItemAnimator] for [BoardList] that behaves correctly when dragging items,
 * into, out of, and within the list.
 *
 * This is essentially identical to [DefaultItemAnimator], but converted to Kotlin and using some
 * minor (but crucial) modifications to make it compatible with the dragging functionality of
 * [BoardViewContainer].
 *
 * Simply using [DefaultItemAnimator] causes jarring animations in almost all dragging scenarios,
 * hence the few crucial modifications made.
 */
class BoardListItemAnimator : SimpleItemAnimator() {

    protected val interpolator = LogarithmicInterpolator()

    private val pendingAdditions = ArrayList<ViewHolder>()
    private val pendingRemovals = ArrayList<ViewHolder>()
    private val pendingMoves = ArrayList<MoveInfo>()
    private val pendingChanges = ArrayList<ChangeInfo>()

    private val additionsList = ArrayList<ArrayList<ViewHolder>>()
    private val movesList = ArrayList<ArrayList<MoveInfo>>()
    private val changesList = ArrayList<ArrayList<ChangeInfo>>()

    private val addAnimations = ArrayList<ViewHolder>()
    private val removeAnimations = ArrayList<ViewHolder>()
    private val moveAnimations = ArrayList<ViewHolder>()
    private val changeAnimations = ArrayList<ViewHolder>()

    // New stuff for dragging aware functionality
    private val onRunPendingAnimations = ArrayList<() -> Unit>()
    private var nextAddIsDraggingItem = false
    private var onAdd: (ViewHolder) -> Unit = {}
    private var draggingItemVH: ViewHolder? = null

    /** Quick and easy way to change all animation durations at once */
    inline var duration: Long
        set(value) {
            this.addDuration = value
            this.changeDuration = value
            this.moveDuration = value
            this.removeDuration = value
        }
        @Deprecated("No getter for duration!", level = DeprecationLevel.ERROR)
        get() = throw Exception("No getter for duration!")

    // when calling LinearLayoutManager.prepareForDrop() the scrolling it does for us will create
    // unnecessary animations for some odd reasons, we thus must ignore them somehow
    // make sure that whenever that method is called, this one is too
    internal fun prepareForDrop() {
        // instead of ignoring the incorrect removals, we simply "animate" them
        // this prevents issues with lingering views and animations that were meant to happen etc
        onRunPendingAnimations.add {
            pendingRemovals.onEach { startBasicRemoveAnimation(it) }.clear()
        }
    }

    internal fun draggingItemInserted(onAdded: (ViewHolder) -> Unit) {
        // Tell animateAdd() that the next add is the draggingItem
        nextAddIsDraggingItem = true
        onAdd = onAdded
    }

    // Useless invisible "animation" used in prepareForDrop
    private inline fun startBasicRemoveAnimation(holder: ViewHolder) {
        val animation = holder.itemView.animate()
        removeAnimations.add(holder)
        animation.setDuration(0)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animator: Animator) {
                        dispatchRemoveStarting(holder)
                    }

                    override fun onAnimationEnd(animator: Animator) {
                        animation.setListener(null)
                        dispatchRemoveFinished(holder)
                        removeAnimations.remove(holder)
                        if (!isRunning) dispatchAnimationsFinished()
                    }
                }).start()
    }

    override fun runPendingAnimations() {
        val removesPending = pendingRemovals.isNotEmpty()
        val movesPending = pendingMoves.isNotEmpty()
        val changesPending = pendingChanges.isNotEmpty()
        val addsPending = pendingAdditions.isNotEmpty()
        if (!removesPending && !movesPending && !addsPending && !changesPending) return

        onRunPendingAnimations.onEach { it() }.clear()

        // First, remove stuff
        pendingRemovals.onEach { startRemoveAnimation(it) }.clear()

        // Next, move stuff
        if (movesPending) {
            val moves = ArrayList(pendingMoves)
            movesList.add(moves)
            pendingMoves.clear()
            val mover: () -> Unit = {
                moves.onEach { startMoveAnimation(it) }.clear()
                movesList.remove(moves)
            }
            if (removesPending) moves[0].holder.itemView.postOnAnimationDelayed(removeDuration, mover)
            else mover()
        }
        // Next, change stuff, to run in parallel with move animations
        if (changesPending) {
            val changes = ArrayList(pendingChanges)
            changesList.add(changes)
            pendingChanges.clear()
            val changer: () -> Unit = {
                changes.onEach { startChangeAnimation(it) }.clear()
                changesList.remove(changes)
            }
            if (removesPending)
                changes[0].oldHolder?.itemView?.postOnAnimationDelayed(removeDuration, changer)
            else changer()
        }
        // Next, add stuff
        if (addsPending) {
            val additions = ArrayList(pendingAdditions)
            additionsList.add(additions)
            pendingAdditions.clear()
            val adder: () -> Unit = {
                additions.onEach { startAddAnimation(it) }.clear()
                additionsList.remove(additions)
            }
            if (removesPending || movesPending || changesPending) {
                val removeDuration = if (removesPending) removeDuration else 0
                val moveDuration = if (movesPending) moveDuration else 0
                val changeDuration = if (changesPending) changeDuration else 0
                val totalDelay = removeDuration + max(moveDuration, changeDuration)
                additions[0].itemView.postOnAnimationDelayed(totalDelay, adder)
            } else adder()
        }
    }

    override fun animateAdd(holder: ViewHolder): Boolean {
        resetAnimation(holder)
        if (nextAddIsDraggingItem) {
            draggingItemVH = holder
            onAdd(holder)
            onAdd = {}
            nextAddIsDraggingItem = false
        }
        holder.itemView.alpha = 0F
        pendingAdditions.add(holder)
        return true
    }

    override fun animateRemove(holder: ViewHolder): Boolean {
        resetAnimation(holder)
        pendingRemovals.add(holder)
        return true
    }

    override fun animateMove(holder: ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        val fromXOffset = fromX + holder.itemView.translationX.toInt()
        val fromYOffset = fromY + holder.itemView.translationY.toInt()
        val view = holder.itemView
        resetAnimation(holder)
        val deltaX = toX - fromXOffset
        val deltaY = toY - fromYOffset
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder)
            return false
        }
        if (deltaX != 0) view.translationX = -deltaX.toFloat()
        if (deltaY != 0) view.translationY = -deltaY.toFloat()
        pendingMoves.add(MoveInfo(holder, fromXOffset, fromYOffset, toX, toY))
        return true
    }

    override fun animateChange(oldHolder: ViewHolder, newHolder: ViewHolder?,
                               fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        if (oldHolder === newHolder) {
            // Don't know how to run change animations when the same view holder is re-used.
            // run a move animation to handle position changes.
            return animateMove(oldHolder, fromX, fromY, toX, toY)
        }
        val prevTranslationX = oldHolder.itemView.translationX
        val prevTranslationY = oldHolder.itemView.translationY
        val prevAlpha = oldHolder.itemView.alpha
        resetAnimation(oldHolder)
        val deltaX = (toX - fromX - prevTranslationX).toInt()
        val deltaY = (toY - fromY - prevTranslationY).toInt()
        // recover prev translation state after ending animation
        oldHolder.itemView.also {
            it.translationX = prevTranslationX
            it.translationY = prevTranslationY
            it.alpha = prevAlpha
        }
        if (newHolder != null) {
            // carry over translation values
            resetAnimation(newHolder)
            newHolder.itemView.also {
                it.translationX = -deltaX.toFloat()
                it.translationY = -deltaY.toFloat()
                it.alpha = 0f
            }
        }
        pendingChanges.add(ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY))
        return true
    }

    override fun endAnimation(item: ViewHolder) {
        val view = item.itemView
        // this will trigger end callback which should set properties to their target values.
        view.animate().cancel()
        pendingMoves.forEachReversedWithIndex { index, moveInfo ->
            if (moveInfo.holder === item) {
                view.translationY = 0F
                view.translationX = 0F
                dispatchMoveFinished(item)
                pendingMoves.removeAt(index)
            }
        }
        endChangeAnimation(pendingChanges, item)
        if (pendingRemovals.remove(item)) {
            view.alpha = 1F
            dispatchRemoveFinished(item)
        }
        if (pendingAdditions.remove(item)) {
            view.alpha = 1F
            dispatchAddFinished(item)
        }
        changesList.forEachReversedWithIndex { index, changes ->
            endChangeAnimation(changes, item)
            if (changes.isEmpty()) changesList.removeAt(index)
        }
        movesList.forEachReversedWithIndex { movesIndex, moves ->
            moves.forEachReversedWithIndex inner@{ moveIndex, moveInfo ->
                if (moveInfo.holder === item) {
                    view.translationY = 0F
                    view.translationX = 0F
                    dispatchMoveFinished(item)
                    moves.removeAt(moveIndex)
                    if (moves.isEmpty()) movesList.removeAt(movesIndex)
                    return@inner
                }
            }
        }
        additionsList.forEachReversedWithIndex { index, additions ->
            if (additions.remove(item)) {
                view.alpha = 1F
                dispatchAddFinished(item)
                if (additions.isEmpty()) additionsList.removeAt(index)
            }
        }
        if (!isRunning) dispatchAnimationsFinished()
    }

    private fun startAddAnimation(holder: ViewHolder) {
        val view = holder.itemView
        val animation = view.animate()
        addAnimations.add(holder)
        // If the holder to be animated is the draggingItemVH
        // then animate it with a useless invisible instant animation
        // this ensures everything will still work while not having any visible side effects
        if (holder == draggingItemVH) {
            draggingItemVH = null
            animation.setDuration(0)
        } else animation.setDuration(addDuration).alpha(1F)
        animation.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animator: Animator) {
                dispatchAddStarting(holder)
            }

            override fun onAnimationCancel(animator: Animator) {
                view.alpha = 1F
            }

            override fun onAnimationEnd(animator: Animator) {
                animation.setListener(null)
                dispatchAddFinished(holder)
                addAnimations.remove(holder)
                if (!isRunning) dispatchAnimationsFinished()
            }
        }).start()
    }

    private fun startMoveAnimation(moveInfo: MoveInfo) {
        val view = moveInfo.holder.itemView
        val deltaX = moveInfo.toX - moveInfo.fromX
        val deltaY = moveInfo.toY - moveInfo.fromY
        if (deltaX != 0) view.animate().translationX(0F)
        if (deltaY != 0) view.animate().translationY(0F)
        val animation = view.animate()
        moveAnimations.add(moveInfo.holder)
        animation.setDuration(moveDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animator: Animator) {
                        dispatchMoveStarting(moveInfo.holder)
                    }

                    override fun onAnimationCancel(animator: Animator) {
                        if (deltaX != 0) view.translationX = 0F
                        if (deltaY != 0) view.translationY = 0F
                    }

                    override fun onAnimationEnd(animator: Animator) {
                        animation.setListener(null)
                        dispatchMoveFinished(moveInfo.holder)
                        moveAnimations.remove(moveInfo.holder)
                        if (!isRunning) dispatchAnimationsFinished()
                    }
                }).start()
    }

    private fun startRemoveAnimation(holder: ViewHolder) {
        val view = holder.itemView
        val animation = view.animate()
        removeAnimations.add(holder)
        animation.setDuration(removeDuration)
                .alpha(0F)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animator: Animator) {
                        dispatchRemoveStarting(holder)
                    }

                    override fun onAnimationEnd(animator: Animator) {
                        animation.setListener(null)
                        view.alpha = 1F
                        dispatchRemoveFinished(holder)
                        removeAnimations.remove(holder)
                        if (!isRunning) dispatchAnimationsFinished()
                    }
                }).start()
    }

    private fun startChangeAnimation(changeInfo: ChangeInfo) {
        val oldHolder = changeInfo.oldHolder
        val oldView = oldHolder?.itemView
        val newHolder = changeInfo.newHolder
        val newView = newHolder?.itemView
        if (oldView != null) {
            val oldViewAnim = oldView.animate().setDuration(changeDuration)
            changeAnimations.add(oldHolder)
            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX.toFloat())
            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY.toFloat())
            oldViewAnim.alpha(0F)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animator: Animator) {
                            dispatchChangeStarting(changeInfo.oldHolder, true)
                        }

                        override fun onAnimationEnd(animator: Animator) {
                            oldViewAnim.setListener(null)
                            oldView.also {
                                it.alpha = 1f
                                it.translationX = 0f
                                it.translationY = 0f
                            }
                            dispatchChangeFinished(oldHolder, true)
                            changeAnimations.remove(oldHolder)
                            if (!isRunning) dispatchAnimationsFinished()
                        }
                    }).start()
        }
        if (newView != null) {
            val newViewAnimation = newView.animate()
            changeAnimations.add(newHolder)
            newViewAnimation.translationX(0F)
                    .translationY(0F)
                    .setDuration(changeDuration)
                    .alpha(1F)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animator: Animator) {
                            dispatchChangeStarting(newHolder, false)
                        }

                        override fun onAnimationEnd(animator: Animator) {
                            newViewAnimation.setListener(null)
                            newView.also {
                                it.alpha = 1f
                                it.translationX = 0f
                                it.translationY = 0f
                            }
                            dispatchChangeFinished(newHolder, false)
                            changeAnimations.remove(newHolder)
                            if (!isRunning) dispatchAnimationsFinished()
                        }
                    }).start()
        }
    }

    override fun isRunning(): Boolean {
        return pendingAdditions.isNotEmpty()
                || pendingChanges.isNotEmpty()
                || pendingMoves.isNotEmpty()
                || pendingRemovals.isNotEmpty()
                || moveAnimations.isNotEmpty()
                || removeAnimations.isNotEmpty()
                || addAnimations.isNotEmpty()
                || changeAnimations.isNotEmpty()
                || movesList.isNotEmpty()
                || additionsList.isNotEmpty()
                || changesList.isNotEmpty()
    }

    override fun endAnimations() {
        pendingMoves.forEachReversedWithIndex { index, move ->
            val view = move.holder.itemView
            view.translationY = 0f
            view.translationX = 0f
            dispatchMoveFinished(move.holder)
            pendingMoves.removeAt(index)
        }
        pendingRemovals.forEachReversedWithIndex { index, viewHolder ->
            dispatchRemoveFinished(viewHolder)
            pendingRemovals.removeAt(index)
        }
        pendingAdditions.forEachReversedWithIndex { index, viewHolder ->
            viewHolder.itemView.alpha = 1F
            dispatchAddFinished(viewHolder)
            pendingAdditions.removeAt(index)
        }

        pendingChanges.forEachReversedByIndex {
            endChangeAnimationIfNecessary(it)
        }
        pendingChanges.clear()
        if (!isRunning) return

        movesList.forEachReversedByIndex { moves ->
            moves.reversedForEachIndexed { index, moveInfo ->
                val item = moveInfo.holder
                val view = item.itemView
                view.translationY = 0f
                view.translationX = 0f
                dispatchMoveFinished(moveInfo.holder)
                moves.removeAt(index)
                if (moves.isEmpty()) movesList.remove(moves)
            }
        }
        additionsList.forEachReversedByIndex { additions ->
            additions.forEachReversedWithIndex { index, viewHolder ->
                val view = viewHolder.itemView
                view.alpha = 1f
                dispatchAddFinished(viewHolder)
                additions.removeAt(index)
                if (additions.isEmpty()) additionsList.remove(additions)
            }
        }
        changesList.forEachReversedByIndex { changes ->
            changes.forEachReversedWithIndex { index, change ->
                endChangeAnimationIfNecessary(change)
                if (changes.isEmpty()) changesList.remove(changes)
            }
        }
        cancelAll(removeAnimations)
        cancelAll(moveAnimations)
        cancelAll(addAnimations)
        cancelAll(changeAnimations)
        dispatchAnimationsFinished()
    }

    private fun endChangeAnimation(infoList: MutableList<ChangeInfo>, item: ViewHolder) {
        infoList.forEachReversedByIndex { changeInfo ->
            if (endChangeAnimationIfNecessary(changeInfo, item))
                if (changeInfo.oldHolder == null && changeInfo.newHolder == null)
                    infoList.remove(changeInfo)
        }
    }

    private fun endChangeAnimationIfNecessary(changeInfo: ChangeInfo) {
        if (changeInfo.oldHolder != null)
            endChangeAnimationIfNecessary(changeInfo, changeInfo.oldHolder)
        if (changeInfo.newHolder != null)
            endChangeAnimationIfNecessary(changeInfo, changeInfo.newHolder)
    }

    private fun endChangeAnimationIfNecessary(changeInfo: ChangeInfo, item: ViewHolder?): Boolean {
        var oldItem = false
        if (changeInfo.newHolder === item) {
            changeInfo.newHolder = null
        } else if (changeInfo.oldHolder === item) {
            changeInfo.oldHolder = null
            oldItem = true
        } else return false
        item?.itemView?.also {
            it.alpha = 1F
            it.translationX = 0F
            it.translationY = 0F
        }
        dispatchChangeFinished(item, oldItem)
        return true
    }

    private fun resetAnimation(holder: ViewHolder) {
        holder.itemView.animate().interpolator = interpolator
        endAnimation(holder)
    }

    private fun cancelAll(viewHolders: List<ViewHolder?>) {
        viewHolders.forEachReversedByIndex { it?.itemView?.animate()?.cancel() }
    }

    data class MoveInfo(var holder: ViewHolder,
                        var fromX: Int,
                        var fromY: Int,
                        var toX: Int,
                        var toY: Int)

    data class ChangeInfo(var oldHolder: ViewHolder?,
                          var newHolder: ViewHolder?,
                          var fromX: Int,
                          var fromY: Int,
                          var toX: Int,
                          var toY: Int)

}