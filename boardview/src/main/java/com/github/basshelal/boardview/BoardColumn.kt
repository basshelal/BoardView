package com.github.basshelal.boardview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * The [View] that [BoardView] will be displaying, this contains a header, a [BoardList] and
 * a footer
 */
open class BoardColumn
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    // TODO: 05-Mar-20 We probably don't need this class! Because BoardViewVH does it for us

    init {
        View.inflate(context, R.layout.view_boardcolumn, this)
    }
}