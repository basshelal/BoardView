package com.github.basshelal.boardview.drag

import android.graphics.PointF
import android.view.View
import com.github.basshelal.boardview.FrameSynchronizer

// TODO: 25-May-20 Make a DragListener / DragBehavior that has a listener for every frame using
//  FrameSynchronizer
open class FrameSyncDragBehavior(view: View) : ObservableDragBehavior(view) {

    /*
     * TODO: 25-May-20 This class needs to execute code every frame, even if the touch location
     *  has not been updated
     *  but without removing onUpdateLocation's functionality. So, ideally there can be code
     *  that isexecuted every frame AND also code that is executed every update?
     *  Or do we just retcon that and do a hard decision that means we no longer have
     *  onUpdateLocation and instead only onNextFrame, that technically is the main purpose of
     *  this class, to execute what onUpdateLocation would but in every frame even if the location
     *  has not been updated so they don't need to both exist Hmm decisions need to be made :/
     */

    protected val frameSynchronizer: FrameSynchronizer = FrameSynchronizer {}

    // onUpdateLocation does nothing, instead onNextFrame must be implemented
    // or reverse, onUpdateLocation now runs every frame
    interface DragListener : ObservableDragBehavior.DragListener {
        fun onNextFrame(dragView: View, touchPoint: PointF)
    }

    abstract class SimpleDragListener : DragListener {
        override fun onStartDrag(dragView: View) {}
        override fun onUpdateLocation(dragView: View, touchPoint: PointF) {}
        override fun onNextFrame(dragView: View, touchPoint: PointF) {}
        override fun onReleaseDrag(dragView: View, touchPoint: PointF) {}
        override fun onEndDrag(dragView: View) {}
        override fun onDragStateChanged(dragView: View, newState: DragState) {}
    }
}