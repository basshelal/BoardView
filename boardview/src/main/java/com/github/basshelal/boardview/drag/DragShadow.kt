@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview.drag

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.github.basshelal.boardview.globalVisibleRectF
import com.github.basshelal.boardview.parentViewGroup

/**
 * An [ImageView] used to represent the draggable "shadow" of any [View].
 *
 * Instead of making a [View] draggable, one can just have one [DragShadow] for every draggable
 * [View] in the container (such as a [Fragment] or a [ViewGroup]), this saves on performance and
 * allows the draggable element appear to move all across the container view even outside the
 * parent of the View it is mirroring. So even if the View being mirrored is heavily nested, the
 * [DragShadow] can make it appear to be movable in any area in the [DragShadow]'s parent which
 * may be an ancestor of the original View's parent.
 *
 * This is useful in Waqti's case for draggable Task Cards, the Board Fragment can have one
 * single [DragShadow] which represents the currently dragging Task, the shadow is a child of the
 * root view of the Fragment so it can exist anywhere inside it, unlike a Task Card which can
 * only exist within the bounds of its parent, the list it is in.
 *
 * This shadow contains no real functionality other than to mirror the [View] that is passed to
 * it in [updateToMatch]. Its dragging functionality is contained in [dragBehavior], an
 * [ObservableDragBehavior] responsible for all the dragging of the shadow. The passed in [View]
 * is not modified here in any way and is thus the caller's responsibility to update its
 * appearance to simulate that the these 2 elements (the View and the Shadow) are actually 1
 * element.
 */
class DragShadow
@JvmOverloads
constructor(context: Context,
            attributeSet: AttributeSet? = null,
            defStyle: Int = 0
) : AppCompatImageView(context, attributeSet, defStyle) {

    val dragBehavior: ObservableDragBehavior = ObservableDragBehavior(this)

    inline infix fun updateToMatch(view: View) {
        updateToMatchBitmapOf(view)
        updateLayoutParams()
        updateToMatchPositionOf(view)
        this.bringToFront()
    }

    inline fun updateLayoutParams() {
        if (this.layoutParams == null) {
            this.layoutParams = ViewGroup.LayoutParams(
                    WRAP_CONTENT,
                    WRAP_CONTENT
            )
        } else {
            updateLayoutParams {
                width = WRAP_CONTENT
                height = WRAP_CONTENT
            }
        }
    }

    inline infix fun updateToMatchBitmapOf(view: View) {
        if (ViewCompat.isLaidOut(this))
            this.setImageBitmap(view.drawToBitmap())
    }

    inline infix fun updateToMatchPositionOf(view: View) {
        this.parentViewGroup?.also { parentVG ->
            val thisParentBounds = parentVG.globalVisibleRectF
            val viewBounds = view.globalVisibleRectF
            val viewParentBounds = view.parentViewGroup?.globalVisibleRectF ?: thisParentBounds

            var shiftX = 0F
            var shiftY = 0F

            val originalWidth = view.width
            val visibleWidth = viewBounds.right - viewBounds.left
            if (visibleWidth < originalWidth) {
                // Left is hidden
                if (viewBounds.left <= viewParentBounds.left) shiftX = originalWidth - visibleWidth
                // We do nothing if right is hidden because x is set in terms of left
            }

            val originalHeight = view.height
            val visibleHeight = viewBounds.bottom - viewBounds.top
            if (visibleHeight < originalHeight) {
                // Top is hidden
                if (viewBounds.top <= viewParentBounds.top) shiftY = originalHeight - visibleHeight
                // We do nothing if bottom is hidden because y is set in terms of top
            }

            // TODO: 12-Mar-20 Is it possible to chop the bitmap to resemble the currently visible parts of the view??
            /*this.setImageBitmap((this.drawable as BitmapDrawable).bitmap.applyCanvas {
                translate(viewBounds.left, viewBounds.top)
                draw(this)
            })*/

            this.x = viewBounds.left - thisParentBounds.left - shiftX
            this.y = viewBounds.top - thisParentBounds.top - shiftY
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return super.onTouchEvent(event) || dragBehavior.onTouchEvent(event)
    }

}