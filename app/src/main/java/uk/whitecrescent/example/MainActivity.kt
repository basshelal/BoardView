package uk.whitecrescent.example

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.github.basshelal.boardview.BoardAdapter
import com.github.basshelal.boardview.BoardContainerAdapter
import com.github.basshelal.boardview.BoardListAdapter
import com.github.basshelal.boardview.BoardViewItemVH
import com.github.basshelal.boardview.BoardViewVH

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

class ExampleBoardContainerAdapter : BoardContainerAdapter() {

    override fun getBoardViewAdapter(): BoardAdapter {
        return ExampleBoardAdapter()
    }

    override fun onCreateListAdapter(position: Int): BoardListAdapter<*> {
        return ExampleBoardListAdapter()
    }

    override fun onCreateListHeader(position: Int): View? {
        TODO("not implemented")
    }

    override fun onCreateFooter(position: Int): View? {
        TODO("not implemented")
    }

}

class ExampleBoardAdapter : BoardAdapter() {

    override fun onViewHolderCreated(holder: BoardViewVH) {

    }

    override fun getItemCount(): Int {
        return 100
    }

    override fun onBindViewHolder(holder: BoardViewVH, position: Int) {

    }
}

class ExampleBoardListAdapter : BoardListAdapter<ItemVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        return ItemVH(View.inflate(parent.context, R.layout.view_itemview, null))
    }

    override fun getItemCount(): Int {
        return 500
    }

    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        holder.textView.text = "Item #$position"
    }

}

class ItemVH(itemView: View) : BoardViewItemVH(itemView) {
    //val textView: TextView = itemView.cardText_textView
}