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

        boardViewContainer.adapter = ExampleBoardContainerAdapter()
    }
}

class ExampleBoardContainerAdapter : BoardContainerAdapter() {

    override fun getBoardViewAdapter(): BoardAdapter {
        return ExampleBoardAdapter(this)
    }

    override fun onCreateListAdapter(): BoardListAdapter<*> {
        return ExampleBoardListAdapter()
    }

    override fun onCreateListHeader(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_header, parentView, false)
    }

    override fun onCreateFooter(parentView: ViewGroup): View? {
        return LayoutInflater.from(parentView.context)
                .inflate(R.layout.view_footer, parentView, false)
    }

}

class ExampleBoardAdapter(adapter: ExampleBoardContainerAdapter) : BoardAdapter(adapter) {

    override fun onViewHolderCreated(holder: BoardViewVH) {

    }

    override fun getItemCount(): Int {
        return 100
    }

    override fun onBindViewHolder(holder: BoardViewVH, position: Int) {
        holder.itemView.header_textView.text = "Header #$position"
    }
}

class ExampleBoardListAdapter : BoardListAdapter<ItemVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        return ItemVH(View.inflate(parent.context, R.layout.view_itemview, null))
    }

    override fun getItemCount(): Int {
        return 99
    }

    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        holder.textView.text = "Item #$position"
    }

}

class ItemVH(itemView: View) : BoardViewItemVH(itemView) {
    val textView: TextView = itemView.cardText_textView
}