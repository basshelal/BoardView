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


    // TODO: 07-Mar-20 To solve the Adapters problem we can either require that Adapters have IDs
    //  which match something in the data structure or (better) we adda another function in the
    //  BoardContiner that returns a Boolean representing whether this adapter (one we have in
    //  the pool) matches or "is sufficient" for this position, true if it's good and false if
    //  not, internally we can do a loop over the Adapter Set and in each onBind we can see
    //  whether an adapter change or creation is needed or not

}

class ExampleBoardContainerAdapter(val board: Board<String>) : BoardContainerAdapter() {

    override fun getBoardViewAdapter(): BoardAdapter {
        return ExampleBoardAdapter(this)
    }

    override fun onCreateListAdapter(position: Int): BoardListAdapter<*> {
        return ExampleBoardListAdapter(this, position)
    }

    override fun onCreateListHeader(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_header, parentView, false).also { it.setOnClickListener { } }
    }

    override fun onCreateFooter(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_footer, parentView, false).also { it.setOnClickListener { } }
    }

    override fun matchListAdapter(boardListAdapter: BoardListAdapter<*>, position: Int): Boolean {
        val adapter = (boardListAdapter as ExampleBoardListAdapter)
        val res = adapter.items.id == board[position].id
        return res
    }

}

class ExampleBoardAdapter(val exampleAdapter: ExampleBoardContainerAdapter)
    : BoardAdapter(exampleAdapter) {

    override fun onViewHolderCreated(holder: BoardViewVH) {

    }

    override fun getItemCount(): Int {
        return exampleAdapter.board.boardLists.size
    }

    override fun onViewHolderBound(holder: BoardViewVH, position: Int) {
        val boardList = exampleAdapter.board[position]
        holder.itemView.header_textView.text = boardList.name
    }
}

class ExampleBoardListAdapter(val exampleAdapter: ExampleBoardContainerAdapter, val position: Int)
    : BoardListAdapter<ItemVH>(exampleAdapter) {

    var items = exampleAdapter.board[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        return ItemVH(LayoutInflater.from(parent.context)
                .inflate(R.layout.view_itemview, parent, false))
    }

    override fun getItemCount(): Int {
        return 99
    }

    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        val listItem = items[position]
        holder.textView.text = listItem.value
    }

    override fun bindAdapter(holder: BoardViewVH, position: Int) {
        items = exampleAdapter.board[position]
    }

}

class ItemVH(itemView: View) : BoardViewItemVH(itemView) {
    val textView: TextView = itemView.cardText_textView
}