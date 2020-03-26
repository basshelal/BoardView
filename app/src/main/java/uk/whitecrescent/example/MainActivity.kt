package uk.whitecrescent.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil.DiffResult.NO_POSITION
import com.github.basshelal.boardview.BoardAdapter
import com.github.basshelal.boardview.BoardColumnViewHolder
import com.github.basshelal.boardview.BoardContainerAdapter
import com.github.basshelal.boardview.BoardItemViewHolder
import com.github.basshelal.boardview.BoardListAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.view_header.view.*
import kotlinx.android.synthetic.main.view_itemview.view.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        boardViewContainer.adapter = ExampleBoardContainerAdapter(exampleBoard)
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

    override fun onSwapBoardViewHolders(oldColumn: BoardColumnViewHolder, newColumn: BoardColumnViewHolder): Boolean {
        val from = oldColumn.adapterPosition
        val to = newColumn.adapterPosition
        return if (from != NO_POSITION && to != NO_POSITION) {
            val value = board[from]
            board.boardLists.removeAt(from)
            board.boardLists.add(to, value)
            true
        } else false
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
            it.setOnClickListener { }
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

    var items = exampleAdapter.board[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        return ItemVH(LayoutInflater.from(parent.context)
                .inflate(R.layout.view_itemview, parent, false)).also { vh ->
            vh.itemView.setOnLongClickListener {
                exampleAdapter.boardViewContainer.startDraggingItem(vh)
                true
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return items[position].id
    }

    override fun getItemCount(): Int {
        return items.items.size
    }

    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        val listItem = items[position]
        holder.textView.text = listItem.value
    }

    override fun bindAdapter(holder: BoardColumnViewHolder, position: Int) {
        items = exampleAdapter.board[position]
    }

}

class ItemVH(itemView: View) : BoardItemViewHolder(itemView) {
    val textView: TextView = itemView.cardText_textView
}