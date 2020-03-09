package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
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

    val adapters = HashSet<BoardListAdapter<*>>()

    // We handle the creation because we return BoardVH that contains columns
    // We have to do this ourselves because we resolve the header, footer and list layout as well
    // as managing list adapters
    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewVH {
        val view = View.inflate(parent.context, R.layout.view_boardcolumn, null) as ConstraintLayout
        val viewHolder = BoardViewVH(view).also { vh ->
            vh.list = vh.itemView.boardListView
        }
        // Header
        adapter?.onCreateListHeader(view)?.also {
            view.header_frameLayout.addView(it)
            viewHolder.header = it
            viewHolder.list?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = view.header_frameLayout.id
                height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            }
        }
        // Footer
        adapter?.onCreateFooter(view)?.also {
            it.id = View.generateViewId()
            view.footer_frameLayout.addView(it)
            viewHolder.footer = it
            viewHolder.list?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                // bottomToTop = view.footer_frameLayout.id
                // height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            }
        }
        onViewHolderCreated(viewHolder)
        return viewHolder
    }

    // callback for caller to do stuff after onCreateViewHolder is called
    open fun onViewHolderCreated(holder: BoardViewVH) {}

    final override fun onBindViewHolder(holder: BoardViewVH, position: Int) {

        logE(adapters.size)

        logE("pos: ${holder.adapterPosition}")

        holder.list?.adapter.also { current ->
            if (current == null) {
                adapter?.onCreateListAdapter(position)?.also {
                    adapters.add(it)
                    holder.list?.adapter = it
                }
            } else {
                // TODO: 09-Mar-20 We need to rebind the data whilst somehow keeping the layout position the same
                (holder.list?.adapter as BoardListAdapter).bindAdapter(holder, position)
                holder.list?.adapter?.notifyDataSetChanged()
                // above is because we need to rebind the adapter contents
            }
        }
        onViewHolderBound(holder, position)
    }

    // callback for caller to do stuff after onBindViewHolder is called
    open fun onViewHolderBound(holder: BoardViewVH, position: Int) {}
}

/**
 * ViewHolder for the [BoardColumn]
 */
open class BoardViewVH(itemView: View) : BaseViewHolder(itemView) {
    var header: View? = null
        internal set
    var list: BoardList? = null
        internal set
    var boardListAdapter: BoardListAdapter<*>? = null
        internal set
    var footer: View? = null
        internal set
}

/**
 * [LinearLayoutManager] used to block caller setting the Layout Manager themselves
 */
internal class InternalLayoutManager(context: Context) : LinearLayoutManager(context)