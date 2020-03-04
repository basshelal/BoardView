package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

abstract class BaseRecyclerView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr)