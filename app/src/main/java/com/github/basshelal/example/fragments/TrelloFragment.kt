package com.github.basshelal.example.fragments

import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.view.animation.Transformation
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
import com.github.basshelal.boardview.drag.ObservableDragBehavior
import com.github.basshelal.example.Board
import com.github.basshelal.example.EXAMPLE_BOARD
import com.github.basshelal.example.dpToPx
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_trello.*
import kotlinx.android.synthetic.main.view_header_trello.view.*
import kotlinx.android.synthetic.main.view_itemview_trello.view.*

private var isZoomedOut = false

class TrelloFragment : Fragment() {

    lateinit var pagerSnapHelper: PagerSnapHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_trello, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // AppBar's rounded corners
        activity?.activityRoot_constraintLayout?.setBackgroundResource(
                R.color.trelloBoardBackground)

        trelloBoardViewContainer.adapter = TrelloBoardContainerAdapter(EXAMPLE_BOARD)

        context?.also { ctx ->
            trelloBoardViewContainer.boardView.columnWidth = (ctx dpToPx 280).toInt()
        }

        pagerSnapHelper = PagerSnapHelper().also {
            it.attachToRecyclerView(trelloBoardViewContainer.boardView)
        }

        trelloBoardViewContainer.itemDragShadow.rotation = 2F
        trelloBoardViewContainer.itemDragShadow.alpha = 0.7F

        trelloBoardViewContainer.itemDragShadow.dragBehavior
                .addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {
                    override fun onStartDrag(dragView: View) {
                        pagerSnapHelper.attachToRecyclerView(null)
                    }

                    override fun onReleaseDrag(dragView: View, touchPoint: PointF) {
                        pagerSnapHelper.attachToRecyclerView(trelloBoardViewContainer.boardView)
                    }
                })

        trelloBoardViewContainer.listDragShadow.rotation = 2F
        trelloBoardViewContainer.listDragShadow.alpha = 0.7F
        trelloBoardViewContainer.listDragShadow.also {
            it.pivotX = 0F
            it.pivotY = 0F
            it.scaleX = 0.5F
            it.scaleY = 0.5F
        }

        trelloBoardViewContainer.listDragShadow.dragBehavior
                .addDragListenerIfNotExists(object : ObservableDragBehavior.SimpleDragListener() {
                    override fun onStartDrag(dragView: View) {
                        isZoomedOut = true
                    }

                    override fun onEndDrag(dragView: View) {
                        isZoomedOut = false
                        (trelloBoardViewContainer.boardView.adapter as?
                                TrelloBoardContainerAdapter.TrelloBoardAdapter)?.notifyAllChanged()
                    }
                })

        zoom_fab.setOnClickListener { zoom() }
    }

    private fun zoom() {
        isZoomedOut = !isZoomedOut
        (trelloBoardViewContainer.boardView.adapter as?
                TrelloBoardContainerAdapter.TrelloBoardAdapter)?.notifyAllChanged()
        zoom_fab.setImageResource(if (isZoomedOut) R.drawable.zoom_in_icon else R.drawable.zoom_out_icon)
        pagerSnapHelper.attachToRecyclerView(if (isZoomedOut) null else trelloBoardViewContainer.boardView)
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
        Log.e("Trello", "onMoveColumn: $targetPosition")
        return false
    }

    override fun onMoveItem(draggingItem: BoardItemViewHolder, targetPosition: Int,
                            draggingColumn: BoardColumnViewHolder, targetColumn: BoardColumnViewHolder): Boolean {
        Log.e("Trello", "onMoveItem: ${targetColumn.adapterPosition} $targetPosition")
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
                topMargin = (ctx dpToPx 16).toInt()
                bottomMargin = (ctx dpToPx 16).toInt()
                marginStart = (ctx dpToPx 10).toInt()
                marginEnd = (ctx dpToPx 10).toInt()
            }
            holder.header?.setOnLongClickListener {
                isZoomedOut = true
                notifyAllChanged()
                boardViewContainer.startDraggingColumn(holder)
                true
            }
        }

        override fun onBindViewHolder(holder: TrelloColumnViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            holder.headerTextView?.text = board[position].name
            // holder.scaleDown() // TODO: 02-Jul-20 Resume here
            holder.itemView.also {
                it.pivotX = 0F
                it.pivotY = 0F
                it.scaleX = if (isZoomedOut) 0.5F else 1F
                it.scaleY = if (isZoomedOut) 0.5F else 1F
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

private class TrelloColumnViewHolder(itemView: View) : BoardColumnViewHolder(itemView) {
    val headerTextView: TextView? get() = header?.trello_header_textView

    fun scaleDown() {
        val initWidth = itemView.width
        val targetWidth = initWidth / 2
        val anim = object : ScaleAnimation(
                1f,
                0.5f,
                1f,
                0.5f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f) {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                super.applyTransformation(interpolatedTime, t)
                itemView.updateLayoutParams {
                    width = width - 1
                }
            }
        }.also {
            it.duration = 500
            it.fillAfter = true
        }
        itemView.startAnimation(anim)
    }

    fun scaleUp() {

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