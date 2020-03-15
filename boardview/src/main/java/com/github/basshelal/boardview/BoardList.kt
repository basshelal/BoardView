package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * The list that displays ItemViews, this is nothing different from an ordinary RecyclerView
 */
class BoardList
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    init {
        layoutManager = SaveRestoreLinearLayoutManager(context).also {
            it.orientation = VERTICAL
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 15
        }
    }

    /**
     * The passed in [adapter] must be a descendant of [BoardListAdapter].
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter is BoardListAdapter) {
            super.setAdapter(adapter)
        } else if (adapter != null)
            logE("BoardList adapter must be a descendant of BoardListAdapter!\n" +
                    "passed in adapter is of type ${adapter::class.simpleName}")
    }

}

/**
 * The adapter responsible for displaying ItemViews, this is nothing different from an ordinary Adapter
 */
abstract class BoardListAdapter<VH : BoardViewItemVH>(
        var adapter: BoardContainerAdapter? = null
) : BaseAdapter<VH>() {

    abstract fun bindAdapter(holder: BoardViewColumnVH, position: Int)
}

/**
 * Contains the ItemView in each list, these are like cards
 * These are used in [BoardList] and its adapter [BoardListAdapter] but are also accessible by
 * [BoardView] and its adapter [BoardAdapter] because they are a part of [BoardViewColumnVH]
 */
open class BoardViewItemVH(itemView: View) : BaseViewHolder(itemView)