package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class BoardView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr)

open class BoardViewAdapter<VH : BoardViewVH> : BaseAdapter<VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        throw NotImplementedError()
    }

    override fun getItemCount(): Int {
        return 0
    }

    override fun onBindViewHolder(holder: VH, position: Int) {

    }

}

open class BoardViewVH(itemView: View) : BaseViewHolder(itemView)