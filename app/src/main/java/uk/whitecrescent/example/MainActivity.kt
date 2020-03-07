package uk.whitecrescent.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.basshelal.boardview.BoardAdapter
import com.github.basshelal.boardview.BoardContainerAdapter
import com.github.basshelal.boardview.BoardListAdapter
import com.github.basshelal.boardview.BoardViewItemVH
import com.github.basshelal.boardview.BoardViewVH
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

    override fun getBoardViewAdapter(): BoardAdapter {
        return ExampleBoardAdapter(this)
    }

    override fun onCreateListAdapter(): BoardListAdapter<*> {
        return ExampleBoardListAdapter(this)
    }

    override fun onCreateListHeader(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_header, parentView, false).also { it.setOnClickListener { } }
    }

    override fun onCreateFooter(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_footer, parentView, false).also { it.setOnClickListener { } }
    }

}

class ExampleBoardAdapter(val exampleAdapter: ExampleBoardContainerAdapter)
    : BoardAdapter(exampleAdapter) {

    override fun onViewHolderCreated(holder: BoardViewVH) {

    }

    override fun getItemCount(): Int {
        return exampleAdapter.board.boardLists.size
    }

    override fun onBindViewHolder(holder: BoardViewVH, position: Int) {
        val boardList = exampleAdapter.board.boardLists[position]
        holder.itemView.header_textView.text = boardList.name
        (holder.boardListAdapter as ExampleBoardListAdapter).boardListId = boardList.id
    }
}

class ExampleBoardListAdapter(val exampleAdapter: ExampleBoardContainerAdapter)
    : BoardListAdapter<ItemVH>(exampleAdapter) {

    var boardListId: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        return ItemVH(LayoutInflater.from(parent.context)
                .inflate(R.layout.view_itemview, parent, false))
    }

    override fun getItemCount(): Int {
        return 99
    }

    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        val listItem = exampleAdapter.board.boardLists[boardListId].items[position]
        holder.textView.text = listItem.value
    }

}

class ItemVH(itemView: View) : BoardViewItemVH(itemView) {
    val textView: TextView = itemView.cardText_textView
}