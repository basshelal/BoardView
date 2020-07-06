package com.github.basshelal.example.fragments

import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.PagerSnapHelper
import com.github.basshelal.R
import com.github.basshelal.boardview.BoardAdapter
import com.github.basshelal.boardview.BoardColumnViewHolder
import com.github.basshelal.boardview.BoardContainerAdapter
import com.github.basshelal.boardview.BoardItemViewHolder
import com.github.basshelal.boardview.BoardListAdapter
import com.github.basshelal.boardview.BoardViewContainer
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import com.github.basshelal.example.Board
import com.github.basshelal.example.EXAMPLE_BOARD
import com.github.basshelal.example.ScaleAnimation
import com.github.basshelal.example.animationListener
import com.github.basshelal.example.dpToPx
import com.github.basshelal.example.now
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_trello.*
import kotlinx.android.synthetic.main.view_header_trello.view.*
import kotlinx.android.synthetic.main.view_itemview_trello.view.*
import kotlin.math.roundToInt

private var isZoomedOut = false
private const val COLUMN_WIDTH_FULL_DP = 280
private const val COLUMN_WIDTH_HALF_DP = 140

class TrelloFragment : Fragment() {

    lateinit var pagerSnapHelper: PagerSnapHelper
    lateinit var boardContainer: BoardViewContainer

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_trello, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // AppBar's rounded corners
        activity?.activityRoot_constraintLayout?.setBackgroundResource(
                R.color.trelloBoardBackground)

        boardContainer = trelloBoardViewContainer

        boardContainer.adapter = TrelloBoardContainerAdapter(EXAMPLE_BOARD)

        context?.also { ctx ->
            boardContainer.boardView.columnWidth = (ctx dpToPx COLUMN_WIDTH_FULL_DP).toInt()
        }

        pagerSnapHelper = PagerSnapHelper().also {
            it.attachToRecyclerView(boardContainer.boardView)
        }

        boardContainer.itemDragShadow.rotation = 2F
        boardContainer.itemDragShadow.alpha = 0.7F

        boardContainer.itemDragShadow.dragBehavior
                .addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {
                    override fun onStartDrag(dragView: View) {
                        pagerSnapHelper.attachToRecyclerView(null)
                    }

                    override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                        pagerSnapHelper.attachToRecyclerView(boardContainer.boardView)
                    }
                })

        boardContainer.listDragShadow.rotation = 2F
        boardContainer.listDragShadow.alpha = 0.7F
        boardContainer.listDragShadow.also {
            it.pivotX = 0F
            it.pivotY = 0F
            it.scaleX = 0.5F
            it.scaleY = 0.5F
        }

        boardContainer.listDragShadow.dragBehavior
                .addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {
                    override fun onStartDrag(dragView: View) {
                        isZoomedOut = true
                    }

                    override fun onEndDrag(dragView: View) {
                        isZoomedOut = false
                        (boardContainer.boardView.adapter as?
                                TrelloBoardContainerAdapter.TrelloBoardAdapter)?.notifyAllChanged()
                    }
                })

        val padding = (context?.dpToPx(8) ?: 0F).toInt()

        boardContainer.boardView.setPadding(padding, 0, padding, 0)
        boardContainer.boardView.clipToPadding = false

        boardContainer.boardView.isOverScrollingEnabled = false

        zoom_fab.setOnClickListener { zoom() }
    }

    private fun zoom() {
        isZoomedOut = !isZoomedOut
        zoom_fab.setImageResource(if (isZoomedOut) R.drawable.zoom_in_icon else R.drawable.zoom_out_icon)
        val onAnimationEnded: (Animation) -> Unit = {
            pagerSnapHelper.attachToRecyclerView(if (isZoomedOut) null else boardContainer.boardView)
        }
        boardContainer.boardView.allVisibleViewHolders
                .map { it as? TrelloColumnViewHolder }
                .filter { it?.isAnimating == false }
                .forEach {
                    if (isZoomedOut) it?.scaleDown(onEnd = onAnimationEnded)
                    else it?.scaleUp(onEnd = onAnimationEnded)
                }
    }
}

private class TrelloBoardContainerAdapter(val board: Board<String>) : BoardContainerAdapter() {

    override val headerLayoutRes: Int? = R.layout.view_header_trello

    override val footerLayoutRes: Int? = R.layout.view_footer_trello

    override val isListWrapContent: Boolean = true

    override val boardViewAdapter: BoardAdapter<TrelloColumnViewHolder>
        get() = TrelloBoardAdapter()

    override fun onCreateListAdapter(position: Int): BoardListAdapter<*> {
        return TrelloListAdapter(position)
    }

    override fun onMoveColumn(draggingColumn: BoardColumnViewHolder, targetPosition: Int): Boolean {
        Log.e("Trello", "onMoveColumn: $targetPosition $now")
        return false
    }

    override fun onMoveItem(draggingItem: BoardItemViewHolder, targetPosition: Int,
                            draggingColumn: BoardColumnViewHolder, targetColumn: BoardColumnViewHolder): Boolean {
        Log.e("Trello", "onMoveItem: ${targetColumn.adapterPosition} $targetPosition $now")
        return false
    }

    inner class TrelloBoardAdapter : BoardAdapter<TrelloColumnViewHolder>(this) {

        override fun getItemId(position: Int): Long = board.boardLists[position].id

        override fun getItemCount(): Int = board.boardLists.size

        override fun createViewHolder(itemView: View): TrelloColumnViewHolder {
            return TrelloColumnViewHolder(itemView)
        }

        override fun onViewHolderCreated(holder: TrelloColumnViewHolder) {
            super.onViewHolderCreated(holder)
            holder.list?.isOverScrollingEnabled = false
            val ctx = holder.itemView.context
            holder.list?.setBackgroundResource(R.color.trelloListColor)
            holder.itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = (ctx dpToPx 10).toInt()
                bottomMargin = (ctx dpToPx 10).toInt()
                marginStart = (ctx dpToPx 10).toInt()
                marginEnd = (ctx dpToPx 10).toInt()
            }
            holder.headerTextView?.setOnLongClickListener {
                isZoomedOut = true
                notifyAllChanged()
                boardViewContainer.startDraggingColumn(holder)
                true
            }
            holder.headerOptionsImageView?.isClickable = true
            holder.itemView.also {
                it.pivotX = 0F
                it.pivotY = 0F
            }
        }

        override fun onBindViewHolder(holder: TrelloColumnViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            holder.headerTextView?.text = board[position].name
            val scale = if (isZoomedOut) 0.5F else 1F
            val halfMargin = (holder.itemView.context?.let { it dpToPx -COLUMN_WIDTH_HALF_DP }
                    ?: 0F).toInt()
            val fullMargin = (holder.itemView.context?.let { it dpToPx 10 } ?: 0F).toInt()
            holder.itemView.also {
                it.scaleX = scale
                it.scaleY = scale
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    rightMargin = if (scale == 1F) fullMargin else halfMargin
                }
            }
        }

        fun notifyAllChanged() {
            (0 until itemCount).forEach { notifyItemChanged(it) }
        }
    }

    inner class TrelloListAdapter(position: Int) : BoardListAdapter<TrelloItemViewHolder>() {

        var list = board[position]

        override fun getItemId(position: Int): Long = list[position].id

        override fun getItemCount(): Int = list.items.size

        override fun bindAdapter(holder: BoardColumnViewHolder, position: Int) {
            list = board[position]
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrelloItemViewHolder {
            return TrelloItemViewHolder(parent).also { vh ->
                val ctx = vh.itemView.context
                vh.itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = (ctx dpToPx 8).toInt()
                    marginEnd = (ctx dpToPx 8).toInt()
                }
                vh.itemView.setOnLongClickListener {
                    boardViewContainer.startDraggingItem(vh)
                    true
                }
            }
        }

        override fun onBindViewHolder(holder: TrelloItemViewHolder, position: Int) {
            holder.textView?.text = list[position].value
        }

    }
}

// TODO: 06-Jul-20 For the zoom animation we can only scale up and down each column
//  this affects the visible width so the column will take the same space but will only fill it
//  by half, we can fix this by 2 ways, current way is using negative margins which has weird
//  side effects and is generally a bad dirty idea, another possible way is by translating each
//  column by x amount it was descaled, that makes more sense but we need special cases for the
//  first and maybe last column
private class TrelloColumnViewHolder(itemView: View) : BoardColumnViewHolder(itemView) {

    val headerTextView: TextView? get() = header?.trello_header_textView
    val headerOptionsImageView: ImageView? get() = header?.trello_header_imageView
    var isAnimating = false

    inline fun scaleDown(crossinline onStart: (Animation) -> Unit = {},
                         crossinline onRepeat: (Animation) -> Unit = {},
                         crossinline onEnd: (Animation) -> Unit = {}) {
        isAnimating = true
        val target = itemView.context?.let { it dpToPx -COLUMN_WIDTH_HALF_DP } ?: 0F
        itemView.startAnimation(ScaleAnimation(1F, 0.5F, 1F, 0.5F,
                Animation.RELATIVE_TO_SELF, 0F,
                Animation.RELATIVE_TO_SELF, 0F) { interpolatedTime: Float, transformation: Transformation ->
            itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = (target * interpolatedTime).roundToInt()
            }
        }.also {
            it.duration = 300
            it.fillBefore = true
            it.fillAfter = true
            it.setAnimationListener(animationListener(onStart, onRepeat, onEnd = {
                itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> { rightMargin = target.toInt() }
                onEnd(it)
                isAnimating = false
            }))
        })
    }

    inline fun scaleUp(crossinline onStart: (Animation) -> Unit = {},
                       crossinline onRepeat: (Animation) -> Unit = {},
                       crossinline onEnd: (Animation) -> Unit = {}) {
        isAnimating = true
        val target = itemView.context?.let { it dpToPx 10 } ?: 0F
        itemView.startAnimation(ScaleAnimation(0.5F, 1F, 0.5F, 1F,
                Animation.RELATIVE_TO_SELF, 0F,
                Animation.RELATIVE_TO_SELF, 0F) { interpolatedTime: Float, transformation: Transformation ->
            itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // TODO: 03-Jul-20 Calculations are wrong I think
                rightMargin += ((target - rightMargin) * interpolatedTime).toInt()
                //  Log.e("TAG", "applyTransformation: $rightMargin $now")
            }
        }.also {
            it.duration = 300
            it.fillBefore = true
            it.fillAfter = true
            it.setAnimationListener(animationListener(onStart, onRepeat, onEnd = {
                onEnd(it)
                isAnimating = false
            }))
        })
    }
}

private class TrelloItemViewHolder(itemView: View) : BoardItemViewHolder(itemView) {
    var cardView: CardView? = null
    var textView: TextView? = null

    constructor(parent: ViewGroup) : this(
            LayoutInflater.from(parent.context).inflate(R.layout.view_itemview_trello, parent, false)
    ) {
        cardView = itemView as? CardView
        textView = itemView.trello_item_textView
    }
}