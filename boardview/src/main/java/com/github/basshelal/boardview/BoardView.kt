package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.view_boardlistview.view.*

class BoardView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    override fun onFinishInflate() {
        super.onFinishInflate()

        layoutManager = LinearLayoutManager(context, HORIZONTAL, false).also {
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 5
        }
    }
}

open class BoardViewAdapter : BaseAdapter<BoardViewVH>() {

    /*
     * If we don't keep any references to Views or Contexts we could technically keep all the
     * ListAdapters in memory like what we've done in Waqti
     */

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewVH {
        return BoardViewVH(View.inflate(parent.context, R.layout.view_boardlistview, null)).also {
            it.itemView.boardListView.adapter = BoardListAdapter<BoardViewItemVH>()
        }
    }

    override fun getItemCount(): Int {
        return 100
    }

    override fun onBindViewHolder(holder: BoardViewVH, position: Int) {

    }

}

open class BoardViewVH(itemView: View) : BaseViewHolder(itemView)