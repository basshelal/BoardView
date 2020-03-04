package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet

/**
 * The list that displays [ItemView]s
 */
class BoardListView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr)