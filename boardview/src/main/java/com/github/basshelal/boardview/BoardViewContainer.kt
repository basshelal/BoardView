package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.github.basshelal.boardview.drag.DragShadow

/**
 * The container that will contain [BoardView] as well as the [DragShadow]s for dragging
 * functionality of [BoardListView] and [ItemView]
 */
class BoardViewContainer
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr)