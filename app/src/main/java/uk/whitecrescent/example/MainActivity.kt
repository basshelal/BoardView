package uk.whitecrescent.example

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.github.basshelal.boardview.BoardListAdapter
import com.github.basshelal.boardview.BoardViewItemVH

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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