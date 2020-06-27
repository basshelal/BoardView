package com.github.basshelal.boardview.drag

import android.graphics.PointF
import android.view.View
import androidx.annotation.CallSuper
import com.github.basshelal.boardview.utils.L
import java.util.Timer
import kotlin.concurrent.timer

abstract class TimerDragListener(val period: Number) : ObservableDragBehavior.DragListener {

    protected var timer: Timer? = null

    @CallSuper
    override fun onStartDrag(dragView: View) {
        timer = timer(period = this.period.L) { onNext() }
    }

    open fun onNext() {}

    @CallSuper
    override fun onEndDrag(dragView: View) {
        timer?.cancel()
        timer = null
    }

    override fun onUpdateLocation(dragView: View, touchPoint: PointF) {}
    override fun onReleaseDrag(dragView: View, touchPoint: PointF) {}
    override fun onDragStateChanged(dragView: View, newState: ObservableDragBehavior.DragState) {}
}