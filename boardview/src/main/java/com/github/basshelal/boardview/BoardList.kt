package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.view_itemview.view.*

/**
 * The list that displays ItemViews, this is nothing different from an ordinary RecyclerView
 */
class BoardList
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardListAdapter) {
            super.setAdapter(adapter)
        } else if (adapter != null)
            Log.e("BoardView", "BoardList adapter must be a descendant of BoardListAdapter" +
                    "passed in adapter is of type ${adapter::class.simpleName}")
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        layoutManager = LinearLayoutManager(context, VERTICAL, false).also {
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 15
        }
    }
}

/**
 * The adapter responsible for displaying ItemViews, this is nothing different from an ordinary Adapter
 */
open class BoardListAdapter<VH : BoardViewItemVH> : BaseAdapter<VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return BoardViewItemVH(View.inflate(parent.context, R.layout.view_itemview, null)) as VH
    }

    override fun getItemCount(): Int {
        return 500
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.textView.text = "Item #$position"
    }

}

/**
 * Contains the ItemView in each list, these are like cards
 */
open class BoardViewItemVH(itemView: View) : BaseViewHolder(itemView) {
    val textView: TextView = itemView.cardText_textView
}