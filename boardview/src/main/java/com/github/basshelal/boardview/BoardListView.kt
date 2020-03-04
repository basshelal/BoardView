package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.view_itemview.view.*

/**
 * The list that displays [ItemView]s
 */
class BoardListView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    override fun onFinishInflate() {
        super.onFinishInflate()

        layoutManager = LinearLayoutManager(context, VERTICAL, false).also {
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 15
        }
    }
}


open class BoardListViewAdapter : BaseAdapter<BoardListViewVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardListViewVH {
        return BoardListViewVH(View.inflate(parent.context, R.layout.view_itemview, null))
    }

    override fun getItemCount(): Int {
        return 100
    }

    override fun onBindViewHolder(holder: BoardListViewVH, position: Int) {
        holder.textView.text = "Item #$position"
    }

}

/**
 * Contains the [ItemView]
 */
open class BoardListViewVH(itemView: View) : BaseViewHolder(itemView) {
    val textView: TextView = itemView.cardText_textView
}