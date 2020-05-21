@file:Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")

package com.github.waqti.frontend.customview

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import com.github.basshelal.R
import com.github.basshelal.boardview.convertDpToPx
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.android.synthetic.main.view_appbar.view.*

class AppBar
@JvmOverloads constructor(
        context: Context,
        attributeSet: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attributeSet, defStyle) {

    companion object {
        const val DEFAULT_ELEVATION = 32F
        const val CORNER_RADIUS = 10
    }

    private val materialShapeDrawable = MaterialShapeDrawable(context, attributeSet, defStyle, 0)

    inline val textView: TextView get() = appBar_textView

    init {
        View.inflate(context, R.layout.view_appbar, this)

        elevation = DEFAULT_ELEVATION

        setBackgroundColor(context.getColorCompat(R.color.colorPrimary))

        textView.setTextColor(Color.WHITE)
        textView.setText(R.string.app_name)

        materialShapeDrawable.apply {
            shapeAppearanceModel = ShapeAppearanceModel.Builder()
                    .setBottomLeftCorner(CornerFamily.ROUNDED, context.convertDpToPx(CORNER_RADIUS))
                    .setBottomRightCorner(CornerFamily.ROUNDED, context.convertDpToPx(CORNER_RADIUS))
                    .build()
        }
    }

    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(Color.TRANSPARENT)
        background = materialShapeDrawable.apply {
            setTint(color)
            elevation = DEFAULT_ELEVATION
        }
    }

}

fun Context.getColorCompat(@ColorRes intId: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        resources.getColor(R.color.colorPrimary, null)
    } else {
        @Suppress("DEPRECATION")
        resources.getColor(R.color.colorPrimary)
    }
}