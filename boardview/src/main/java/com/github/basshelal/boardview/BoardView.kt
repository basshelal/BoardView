package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.view_boardcolumn.view.*

open class BoardView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    init {
        layoutManager = InternalLayoutManager(context).also {
            it.orientation = HORIZONTAL
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 5
        }
    }

    /**
     * You are not supposed to set the [LayoutManager] of BoardView, this is managed internally!
     * You can change properties of the [LayoutManager] by calling [getLayoutManager].
     */
    override fun setLayoutManager(lm: LayoutManager?) {
        if (lm is InternalLayoutManager) {
            super.setLayoutManager(lm)
        } else if (lm != null)
            logE("You are not allowed to set the Layout Manager of BoardView!\n" +
                    "Passed in ${lm::class.simpleName}")
    }

    /**
     * The passed in [adapter] must be a descendant of [BoardAdapter].
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardAdapter) {
            super.setAdapter(adapter)
        } else if (adapter != null)
            logE("BoardView adapter must be a descendant of BoardAdapter!\n" +
                    "passed in adapter is of type ${adapter::class.simpleName}")
    }
}

abstract class BoardAdapter(
        var adapter: BoardContainerAdapter? = null
) : BaseAdapter<BoardViewVH>() {

    /*
     * If we don't keep any references to Views or Contexts we could technically keep all the
     * ListAdapters in memory like what we've done in Waqti
     */

    // We handle the creation because we return BoardVH that contains columns
    // We have to do this ourselves because we resolve the header, footer and list layout as well
    // as managing list adapters
    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewVH {
        val view = View.inflate(parent.context, R.layout.view_boardcolumn, null) as ConstraintLayout
        val viewHolder = BoardViewVH(view).also { vh ->
            adapter?.onCreateListAdapter()?.also {
                vh.itemView.boardListView.adapter = it
            }
            vh.list = vh.itemView.boardListView
        }
        adapter?.onCreateListHeader(view)?.also {
            view.header_frameLayout.addView(it)
            viewHolder.header = it
        }
        adapter?.onCreateFooter(view)?.also {
            view.footer_frameLayout.addView(it)
            viewHolder.footer = it
        }
        onViewHolderCreated(viewHolder)
        return viewHolder
    }

    // callback for caller to do stuff after onCreateViewHolder is called
    open fun onViewHolderCreated(holder: BoardViewVH) {}

}

/**
 * ViewHolder for the [BoardColumn]
 */
open class BoardViewVH(itemView: View) : BaseViewHolder(itemView) {
    var header: View? = null
        internal set
    var list: View? = null
        internal set
    var footer: View? = null
        internal set
}

/**
 * [LinearLayoutManager] used to block caller setting the Layout Manager themselves
 */
internal class InternalLayoutManager(context: Context) : LinearLayoutManager(context)