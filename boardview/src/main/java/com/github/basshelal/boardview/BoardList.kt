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
        layoutManager = InternalLayoutManager(context).also {
            it.orientation = VERTICAL
            it.isItemPrefetchEnabled = true
            it.initialPrefetchItemCount = 15
        }
    }

    /**
     * You are not supposed to set the [LayoutManager] of BoardList, this is managed internally!
     * You can change properties of the [LayoutManager] by calling [getLayoutManager].
     */
    override fun setLayoutManager(lm: LayoutManager?) {
        if (lm is InternalLayoutManager) {
            super.setLayoutManager(lm)
        } else if (lm != null)
            logE("You are not allowed to set the Layout Manager of BoardList!\n" +
                    "Passed in ${lm::class.simpleName}")
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
) : BaseAdapter<VH>()

/**
 * Contains the ItemView in each list, these are like cards
 */
// TODO: 06-Mar-20 Probably remove later
open class BoardViewItemVH(itemView: View) : BaseViewHolder(itemView)