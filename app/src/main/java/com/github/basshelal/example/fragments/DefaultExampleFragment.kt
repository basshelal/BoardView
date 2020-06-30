package com.github.basshelal.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import com.github.basshelal.R
import com.github.basshelal.boardview.BoardAdapter
import com.github.basshelal.boardview.BoardColumnViewHolder
import com.github.basshelal.boardview.BoardContainerAdapter
import com.github.basshelal.boardview.BoardItemViewHolder
import com.github.basshelal.boardview.BoardListAdapter
import com.github.basshelal.example.Board
import com.github.basshelal.example.StringListItem
import com.github.basshelal.example.exampleBoard
import kotlinx.android.synthetic.main.fragment_default_example.*
import kotlinx.android.synthetic.main.view_header.view.*
import kotlinx.android.synthetic.main.view_itemview.view.*

class DefaultExampleFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_default_example, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        defaultBoardViewContainer.adapter = ExampleBoardContainerAdapter(exampleBoard)
    }
}

private class ExampleBoardContainerAdapter(val board: Board<String>) : BoardContainerAdapter() {

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
        return if (from != DiffUtil.DiffResult.NO_POSITION && targetPosition != DiffUtil.DiffResult.NO_POSITION) {
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

        if (oldColumnPosition == DiffUtil.DiffResult.NO_POSITION || newColumnPosition == DiffUtil.DiffResult.NO_POSITION ||
                oldItemPosition == DiffUtil.DiffResult.NO_POSITION || newItemPosition == DiffUtil.DiffResult.NO_POSITION) return false

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
}

private class ExampleBoardAdapter(val exampleAdapter: ExampleBoardContainerAdapter)
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

private class ExampleBoardListAdapter(val exampleAdapter: ExampleBoardContainerAdapter, val position: Int)
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
            if (pos != DiffUtil.DiffResult.NO_POSITION && boardList.items.isNotEmpty()) {
                boardList.items.removeAt(pos)
                notifyItemRemoved(pos)
            }
        }
    }

    override fun bindAdapter(holder: BoardColumnViewHolder, position: Int) {
        boardList = exampleAdapter.board[position]
    }

}

private class ItemVH(itemView: View) : BoardItemViewHolder(itemView) {
    val textView: TextView = itemView.cardText_textView
}