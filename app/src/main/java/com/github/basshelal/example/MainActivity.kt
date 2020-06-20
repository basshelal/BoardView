package com.github.basshelal.example

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil.DiffResult.NO_POSITION
import com.github.basshelal.R
import com.github.basshelal.boardview.BoardAdapter
import com.github.basshelal.boardview.BoardColumnViewHolder
import com.github.basshelal.boardview.BoardContainerAdapter
import com.github.basshelal.boardview.BoardItemViewHolder
import com.github.basshelal.boardview.BoardListAdapter
import com.github.basshelal.boardview.drag.ViewAwareDragListener
import com.github.basshelal.boardview.randomColor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.view_header.view.*
import kotlinx.android.synthetic.main.view_itemview.view.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        boardViewContainer.adapter = ExampleBoardContainerAdapter(exampleBoard)

        boardViewContainer.itemDragShadow.dragBehavior.addDragListenerIfNotExists(object : ViewAwareDragListener() {
            override fun onDragOverView(dragView: View, touchPoint: PointF, targetView: View?) {
                if (targetView == appBar) {
                    appBar.setBackgroundColor(randomColor)
                }
            }
        })
    }
}

class ExampleBoardContainerAdapter(val board: Board<String>) : BoardContainerAdapter() {

    override val boardViewAdapter: BoardAdapter
        get() = ExampleBoardAdapter(this)

    override fun onCreateListAdapter(position: Int): BoardListAdapter<*> {
        return ExampleBoardListAdapter(this, position)
    }

    override fun onCreateListHeader(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_header, parentView, false)
    }

    override fun onCreateFooter(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_footer, parentView, false)
    }

    override fun onMoveColumn(draggingColumn: BoardColumnViewHolder,
                              targetPosition: Int): Boolean {
        val from = draggingColumn.adapterPosition
        return if (from != NO_POSITION && targetPosition != NO_POSITION) {
            val value = board[from]
            board.boardLists.removeAt(from)
            board.boardLists.add(targetPosition, value)
            true
        } else false
    }

    override fun onMoveItem(draggingItem: BoardItemViewHolder, targetPosition: Int,
                            draggingColumn: BoardColumnViewHolder, targetColumn: BoardColumnViewHolder): Boolean {
        val oldColumnPosition = draggingColumn.adapterPosition
        val newColumnPosition = targetColumn.adapterPosition
        val oldItemPosition = draggingItem.adapterPosition
        val newItemPosition = targetPosition

        if (oldColumnPosition == newColumnPosition) {
            if (oldItemPosition == newItemPosition) return false
            else {
                val boardList = board.boardLists[oldColumnPosition]
                val value = boardList[oldItemPosition]
                boardList.items.removeAt(oldItemPosition)
                boardList.items.add(newItemPosition, value)
                return true
            }
        } else {
            val fromBoardList = board.boardLists[oldColumnPosition]
            val toBoardList = board.boardLists[newColumnPosition]
            val value = fromBoardList[oldItemPosition]
            fromBoardList.items.removeAt(oldItemPosition)
            toBoardList.items.add(newItemPosition, value)
            return true
        }
    }

    fun onInsertItemViewHolder(item: BoardItemViewHolder,
                               oldColumn: BoardColumnViewHolder, newColumn: BoardColumnViewHolder): Boolean {
        val oldColumnPosition = oldColumn.adapterPosition
        val newColumnPosition = newColumn.adapterPosition
        val itemPosition = item.adapterPosition

        if (oldColumnPosition == newColumnPosition) return false
        else {
            val fromBoardList = board.boardLists[oldColumnPosition]
            val toBoardList = board.boardLists[newColumnPosition]
            val value = fromBoardList[itemPosition]
            fromBoardList.items.removeAt(itemPosition)
            toBoardList.items.add(value)
            return true
        }
    }

}

class ExampleBoardAdapter(val exampleAdapter: ExampleBoardContainerAdapter)
    : BoardAdapter(exampleAdapter) {

    private var boardMode: BoardMode = BoardMode.MULTI

    override fun onViewHolderCreated(holder: BoardColumnViewHolder) {
        holder.header?.also {
            it.setOnClickListener {
                when (boardMode) {
                    BoardMode.MULTI -> exampleAdapter.boardViewContainer.boardView.switchToSingleColumnModeAt(holder.adapterPosition)
                    BoardMode.SINGLE -> exampleAdapter.boardViewContainer.boardView.switchToMultiColumnMode(500)
                }
                boardMode = boardMode.toggle()
            }
            it.setOnLongClickListener {
                exampleAdapter.boardViewContainer.startDraggingColumn(holder)
                true
            }
        }
        holder.footer?.also {
            it.setOnClickListener {
                val list = exampleAdapter.board[holder.adapterPosition]
                val last = list.items.last()
                list.items.add(StringListItem(last.id + 1, "Item #${last.id + 1}"))
                holder.boardListAdapter?.notifyItemInserted(list.items.lastIndex)
                holder.list?.smoothScrollToPosition(list.items.lastIndex)
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return exampleAdapter.board[position].id
    }

    override fun getItemCount(): Int {
        return exampleAdapter.board.boardLists.size
    }

    override fun onBindViewHolder(holder: BoardColumnViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val boardList = exampleAdapter.board[position]
        holder.itemView.header_textView.text = boardList.name
    }

    private enum class BoardMode {
        SINGLE, MULTI;

        fun toggle(): BoardMode {
            return if (this == SINGLE) MULTI else SINGLE
        }
    }
}

class ExampleBoardListAdapter(val exampleAdapter: ExampleBoardContainerAdapter, val position: Int)
    : BoardListAdapter<ItemVH>(exampleAdapter) {

    var boardList = exampleAdapter.board[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        return ItemVH(LayoutInflater.from(parent.context)
                .inflate(R.layout.view_itemview, parent, false))
                .also { itemVH ->
                    itemVH.itemView.setOnLongClickListener {
                        exampleAdapter.boardViewContainer.startDraggingItem(itemVH)
                        true
                    }
                }
    }

    override fun getItemId(position: Int): Long {
        return boardList[position].id
    }

    override fun getItemCount(): Int {
        return boardList.items.size
    }

    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        val listItem = boardList[position]
        holder.textView.text = listItem.value
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != NO_POSITION && boardList.items.isNotEmpty()) {
                boardList.items.removeAt(pos)
                notifyItemRemoved(pos)
            }
        }
    }

    override fun bindAdapter(holder: BoardColumnViewHolder, position: Int) {
        boardList = exampleAdapter.board[position]
    }

}

class ItemVH(itemView: View) : BoardItemViewHolder(itemView) {
    val textView: TextView = itemView.cardText_textView
}