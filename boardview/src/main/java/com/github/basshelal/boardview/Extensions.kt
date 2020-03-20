@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.text.Editable
import android.text.SpannableStringBuilder
import android.util.DisplayMetrics
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Interpolator
import android.view.animation.Transformation
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.jetbrains.anko.Orientation
import org.jetbrains.anko.childrenRecursiveSequence
import org.jetbrains.anko.configuration
import org.jetbrains.anko.contentView
import org.jetbrains.anko.displayMetrics
import org.jetbrains.anko.find
import org.jetbrains.anko.inputMethodManager
import org.jetbrains.anko.windowManager
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

inline val Number.I: Int get() = this.toInt()
inline val Number.D: Double get() = this.toDouble()
inline val Number.F: Float get() = this.toFloat()
inline val Number.L: Long get() = this.toLong()

inline val now: Long get() = System.currentTimeMillis()

inline operator fun <reified V : View?> V?.invoke(block: V.() -> Unit) = this?.apply(block)

inline fun View.shortSnackBar(string: String) = Snackbar.make(this, string, Snackbar.LENGTH_SHORT).show()

inline fun View.longSnackBar(string: String) = Snackbar.make(this, string, Snackbar.LENGTH_LONG).show()

inline fun View.infSnackBar(string: String) = Snackbar.make(this, string, Snackbar.LENGTH_INDEFINITE).show()

inline fun View.hideKeyboard() {
    if (this is EditText) this.setSelection(0)
    context.inputMethodManager.hideSoftInputFromWindow(this.windowToken, 0)
}

inline fun View.showKeyboard() {
    context.inputMethodManager.showSoftInput(this, 0)
}

inline fun View.requestFocusAndShowKeyboard() {
    requestFocus()
    showKeyboard()
}

inline fun View.clearFocusAndHideKeyboard() {
    clearFocus()
    hideKeyboard()
}

inline val View.locationOnScreen: Point
    get() {
        val point = IntArray(2).also {
            this.getLocationOnScreen(it)
        }
        return Point(point[0], point[1])
    }

inline fun View.fadeIn(durationMillis: Long = 250) {
    this.startAnimation(AlphaAnimation(0F, 1F).apply {
        duration = durationMillis
        fillAfter = true
    })
}

inline fun View.fadeOut(durationMillis: Long = 250) {
    this.startAnimation(AlphaAnimation(1F, 0F).apply {
        duration = durationMillis
        fillAfter = true
    })
}

inline val View.isTransparent: Boolean
    get() = alpha == 0F

inline val View.isClear: Boolean
    get() = alpha == 1F

inline fun View.removeOnClickListener() = this.setOnClickListener(null)

val View.parents: List<ViewGroup>
    get() {
        val result = ArrayList<ViewGroup>()
        var current = parent
        while (current != null && current is ViewGroup) {
            result.add(current)
            current = current.parent
        }
        return result
    }

inline val View.parentView: View?
    get() = parent as? View?

inline val View.parentViewGroup: ViewGroup?
    get() = parent as? ViewGroup?

inline val View.rootViewGroup: ViewGroup?
    get() = this.rootView as? ViewGroup

inline val View.globalVisibleRect: Rect
    get() = Rect().also { this.getGlobalVisibleRect(it) }

inline val View.globalVisibleRectF: RectF
    get() = globalVisibleRect.toRectF()

inline val View.localVisibleRect: Rect
    get() = Rect().also { this.getLocalVisibleRect(it) }

inline val View.localVisibleRectF: RectF
    get() = localVisibleRect.toRectF()

inline val RectF.detailedString: String
    get() = "L: $left, T: $top, R: $right, B: $bottom"

inline val Rect.detailedString: String
    get() = "L: $left, T: $top, R: $right, B: $bottom"

inline fun <reified T : View> View.find(@IdRes id: Int, apply: T.() -> Unit): T = find<T>(id).apply(apply)

inline val View.millisPerFrame get() = 1000F / context.windowManager.defaultDisplay.refreshRate

// Screen width that the context is able to use, this doesn't include navigation bars
inline val View.usableScreenWidth: Int get() = context.displayMetrics.widthPixels

// Screen height that the context is able to use, this doesn't include navigation bars
inline val View.usableScreenHeight: Int get() = context.displayMetrics.heightPixels

inline val View.realScreenWidth: Int
    get() = Point().also {
        context.windowManager.defaultDisplay.getRealSize(it)
    }.x

inline val View.realScreenHeight: Int
    get() = Point().also {
        context.windowManager.defaultDisplay.getRealSize(it)
    }.y

class LogarithmicInterpolator : Interpolator {
    override fun getInterpolation(input: Float): Float {
        // kotlin.math.log(x = (4.0 * input.D) + 1.0, base = 5.0)
        // log10((9.0 * input.D) + 1.0)
        return log2(input + 1)
    }
}

inline operator fun Interpolator.get(float: Float) = this.getInterpolation(float)

inline val View.orientation: Orientation
    get() = when (context.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> Orientation.LANDSCAPE
        Configuration.ORIENTATION_PORTRAIT -> Orientation.PORTRAIT
        else -> if (usableScreenHeight >= usableScreenWidth)
            Orientation.PORTRAIT else Orientation.LANDSCAPE
    }

inline val ViewGroup.allChildren: List<View>
    get() = this.childrenRecursiveSequence().toList()

inline fun View.bottomHorizontalRect(top: Float): RectF {
    return this.globalVisibleRectF.also { it.top = top }
}

inline fun View.topHorizontalRect(bottom: Float): RectF {
    return this.globalVisibleRectF.also { it.bottom = bottom }
}

inline fun View.leftVerticalRect(right: Float): RectF {
    return this.globalVisibleRectF.also { it.right = right }
}

inline fun View.rightVerticalRect(left: Float): RectF {
    return this.globalVisibleRectF.also { it.left = left }
}

inline fun View.updateLayoutParamsSafe(block: ViewGroup.LayoutParams.() -> Unit) {
    layoutParams?.apply(block)
    requestLayout()
}

@JvmName("updateLayoutParamsSafeTyped")
inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParamsSafe(block: T.() -> Unit) {
    (layoutParams as? T)?.apply(block)
    requestLayout()
}

inline fun RecyclerView.findChildViewUnderRaw(rawX: Float, rawY: Float): View? {
    val rect = this.globalVisibleRectF
    val x = rawX - rect.left
    val y = rawY - rect.top
    return this.findChildViewUnder(x, y)
}

inline fun RecyclerView.scrollToEnd() {
    if (this.adapter != null) {
        this.scrollToPosition(adapter!!.lastPosition)
    }
}

inline fun RecyclerView.smoothScrollToEnd() {
    if (this.adapter != null) {
        this.smoothScrollToPosition(adapter!!.lastPosition)
    }
}

inline fun RecyclerView.scrollToStart() {
    if (this.adapter != null) {
        this.scrollToPosition(0)
    }
}

inline fun RecyclerView.smoothScrollToStart() {
    if (this.adapter != null) {
        this.smoothScrollToPosition(0)
    }
}

inline fun RecyclerView.addOnScrollListener(
        crossinline onScrollStateChanged: (newState: Int) -> Unit = { _ -> },
        crossinline onScrolled: (dx: Int, dy: Int) -> Unit = { _, _ -> }) {
    addOnScrollListener(onScrollListener(onScrollStateChanged, onScrolled))
}

inline fun RecyclerView.onScrollListener(
        crossinline onScrollStateChanged: (newState: Int) -> Unit = { _ -> },
        crossinline onScrolled: (dx: Int, dy: Int) -> Unit = { _, _ -> }) =
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onScrolled(dx, dy)
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                onScrollStateChanged(newState)
            }
        }


inline fun recycledViewPool(maxCount: Int) =
        object : RecyclerView.RecycledViewPool() {
            override fun setMaxRecycledViews(viewType: Int, max: Int) =
                    super.setMaxRecycledViews(viewType, maxCount)
        }

inline val RecyclerView.Adapter<*>.lastPosition: Int
    get() = this.itemCount - 1

inline fun RecyclerView.Adapter<*>.notifySwapped(fromPosition: Int, toPosition: Int) {
    notifyItemRemoved(fromPosition)
    notifyItemInserted(fromPosition)
    notifyItemRemoved(toPosition)
    notifyItemInserted(toPosition)
}

inline fun RecyclerView.isViewPartiallyVisible(view: View): Boolean =
        this.layoutManager?.isViewPartiallyVisible(view, false, true) ?: false

inline fun RecyclerView.isViewCompletelyVisible(view: View): Boolean =
        this.layoutManager?.isViewPartiallyVisible(view, true, true) ?: false

inline fun RecyclerView.isViewHolderPartiallyVisible(vh: RecyclerView.ViewHolder) =
        isViewPartiallyVisible(vh.itemView)

inline fun RecyclerView.isViewHolderCompletelyVisible(vh: RecyclerView.ViewHolder) =
        isViewCompletelyVisible(vh.itemView)

inline val RecyclerView.horizontalScrollOffset: Int get() = computeHorizontalScrollOffset()
inline val RecyclerView.verticalScrollOffset: Int get() = computeVerticalScrollOffset()
inline val RecyclerView.maxHorizontalScroll: Int get() = computeHorizontalScrollRange() - computeHorizontalScrollExtent()
inline val RecyclerView.maxVerticalScroll: Int get() = computeVerticalScrollRange() - computeVerticalScrollExtent()

inline fun RecyclerView.doOnFinishScroll(
        crossinline action: (recyclerView: RecyclerView) -> Unit) {
    if (this.scrollState == RecyclerView.SCROLL_STATE_IDLE) action(this)
    else addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE ||
                    recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                removeOnScrollListener(this)
                action(recyclerView)
            }
        }
    })
}

inline fun animation(crossinline applyTransformation:
                     (interpolatedTime: Float, transformation: Transformation) -> Unit): Animation {
    return object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            applyTransformation(interpolatedTime, t)
        }
    }
}

inline fun Animation.onEnd(crossinline block: (Animation) -> Unit) {
    setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationEnd(animation: Animation) {
            block(animation)
        }

        override fun onAnimationRepeat(animation: Animation) {}
        override fun onAnimationStart(animation: Animation) {}

    })
}

inline fun Transition.onEnd(crossinline block: (Transition) -> Unit) {
    addListener(object : Transition.TransitionListener {
        override fun onTransitionEnd(transition: Transition) {
            block(transition)
        }

        override fun onTransitionResume(transition: Transition) {}
        override fun onTransitionPause(transition: Transition) {}
        override fun onTransitionCancel(transition: Transition) {}
        override fun onTransitionStart(transition: Transition) {}
    })
}

inline fun logE(message: Any?, tag: String = "BoardView") {
    if (BuildConfig.DEBUG) Log.e(tag, message.toString())
}

inline fun Any.log(message: Any?, tag: String = this::class.simpleName.toString()) {
    logE(message, tag)
    when (this) {
        is View -> this.shortSnackBar(message.toString())
        is Fragment -> this.view?.shortSnackBar(message.toString())
        is Activity -> this.contentView?.shortSnackBar(message.toString())
    }
}

inline fun Activity.checkWritePermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1)
    }
}

inline fun FragmentManager.commitTransaction(block: FragmentTransaction.() -> Unit) {
    this.beginTransaction().apply(block).commit()
}

inline fun ComponentActivity.addOnBackPressedCallback(crossinline onBackPressed: () -> Unit) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = onBackPressed()
    })
}

inline fun String.toEditable() = SpannableStringBuilder(this)

inline val Editable?.isValid: Boolean get() = this != null && this.isNotBlank()

inline val MotionEvent.actionString: String get() = MotionEvent.actionToString(this.action)

inline fun MotionEvent.obtainCopy(): MotionEvent = MotionEvent.obtain(this)

@SuppressLint("Recycle")
inline fun obtainTouchEvent(action: Int, x: Number, y: Number): MotionEvent =
        MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                        action, x.F, y.F, 0)
                .also { it.source = InputDevice.SOURCE_TOUCHSCREEN }

inline fun MotionEvent.cancel(): MotionEvent =
        this.also { it.action = MotionEvent.ACTION_CANCEL }

/**
 * This method converts dp unit to equivalent pixels, depending on device density.
 *
 * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
 * @return A float value to represent px equivalent to dp depending on device density
 */
inline infix fun Context.convertDpToPx(dp: Number): Float =
        (dp.F * (this.resources.displayMetrics.densityDpi.F / DisplayMetrics.DENSITY_DEFAULT))

/**
 * This method converts device specific pixels to density independent pixels.
 *
 * @param px A value in px (pixels) unit. Which we need to convert into db
 * @return A float value to represent dp equivalent to px value
 */
inline infix fun Context.convertPxToDp(px: Number): Float =
        (px.F / (this.resources.displayMetrics.densityDpi.F / DisplayMetrics.DENSITY_DEFAULT))

inline fun RectF.verticalPercent(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.y - min) / (max - min)) * 100F)
}

inline fun RectF.verticalPercentInverted(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.y - max) / (min - max)) * 100F)
}

inline fun RectF.horizontalPercent(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.x - min) / (max - min)) * 100F)
}

inline fun RectF.horizontalPercentInverted(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.y - max) / (min - max)) * 100F)
}

inline fun <T> Subscriber(
        crossinline onError: (Throwable?) -> Unit = {},
        crossinline onComplete: () -> Unit = {},
        crossinline onSubscribe: (Subscription?) -> Unit = {},
        crossinline onNext: (T) -> Unit = {}
) = object : Subscriber<T> {
    override fun onNext(t: T) = onNext(t)
    override fun onError(t: Throwable?) = onError(t)
    override fun onComplete() = onComplete()
    override fun onSubscribe(s: Subscription?) = onSubscribe(s)
}

inline fun <T> Observer(
        crossinline onError: (Throwable) -> Unit = {},
        crossinline onComplete: () -> Unit = {},
        crossinline onSubscribe: (Disposable) -> Unit = {},
        crossinline onNext: (T) -> Unit = {}
) = object : Observer<T> {
    override fun onNext(t: T) = onNext(t)
    override fun onError(t: Throwable) = onError(t)
    override fun onComplete() = onComplete()
    override fun onSubscribe(d: Disposable) = onSubscribe(d)
}

inline fun <T> Observable<T>.onAndroid() = this.observeOn(AndroidSchedulers.mainThread())

/**
 * Provides an Observable that can be used to execute code on the computation thread and notify
 * the main thread, this means this Observable can be used to safely modify any UI elements on a
 * background thread
 */
inline fun <T> T.androidObservable(): Observable<T> {
    return Observable.just(this)
            .onAndroid()
            .subscribeOn(Schedulers.computation())
}

/**
 * Executes the code provided by [onNext] continuously when the provided [predicate] is true,
 * [onNext] will only be invoked once but if the [predicate] becomes false and then true again
 * [onNext] will execute again. All this is done on a background thread and notified on the main
 * thread just like [androidObservable].
 */
inline fun <reified T> T.doInBackgroundWhen(crossinline predicate: (T) -> Boolean,
                                            crossinline onNext: T.() -> Unit,
                                            period: Number = 100,
                                            timeUnit: java.util.concurrent.TimeUnit =
                                                    java.util.concurrent.TimeUnit.MILLISECONDS): Disposable {
    var invoked = false
    return Observable.interval(period.toLong(), timeUnit, Schedulers.computation())
            .onAndroid()
            .subscribeOn(Schedulers.computation())
            .subscribe({
                if (!predicate(this)) invoked = false
                if (predicate(this) && !invoked) {
                    onNext(this)
                    invoked = true
                }
            }, {
                logE("Error on doInBackgroundAsync, provided ${T::class.java}")
                logE(it.message)
                it.printStackTrace()
                throw it
            })
}

/**
 * Executes the code provided by [onNext] once as soon as the provided [predicate] is true.
 * All this is done on a background thread and notified on the main thread just like
 * [androidObservable].
 */
inline fun <reified T> T.doInBackgroundOnceWhen(crossinline predicate: (T) -> Boolean,
                                                period: Number = 100,
                                                timeUnit: java.util.concurrent.TimeUnit =
                                                        java.util.concurrent.TimeUnit.MILLISECONDS,
                                                crossinline onNext: T.() -> Unit): Disposable {
    var done = false
    return Observable.interval(period.toLong(), timeUnit, Schedulers.computation())
            .onAndroid()
            .subscribeOn(Schedulers.computation())
            .takeWhile { !done }
            .subscribe({
                if (predicate(this)) {
                    onNext(this)
                    done = true
                }
            }, {
                logE("Error on doInBackgroundAsync, provided ${T::class.java}")
                logE(it.message)
                it.printStackTrace()
                throw it
            })
}

inline fun <reified T> T.doInBackgroundAsync(crossinline onNext: T.() -> Unit): Disposable {
    return Observable.just(this)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .subscribe({
                it.apply(onNext)
            }, {
                logE("Error on doInBackgroundAsync, provided ${T::class.java}")
                logE(it.message)
                it.printStackTrace()
                throw it
            })
}

inline fun <reified T> T.doInBackground(crossinline onNext: T.() -> Unit): Disposable {
    return androidObservable()
            .subscribe({
                it.apply(onNext)
            }, {
                logE("Error on doInBackground, provided ${T::class.java}")
                logE(it.message)
                it.printStackTrace()
                throw it
            })
}

inline fun <reified T> T.doInBackgroundDelayed(delayMillis: Long,
                                               crossinline onNext: T.() -> Unit): Disposable {
    return androidObservable()
            .delay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribe({
                it.apply(onNext)
            }, {
                logE("Error on doInBackgroundDelayed, provided ${T::class.java} with delay $delayMillis")
                logE(it.message)
                it.printStackTrace()
                throw it
            })
}

inline fun <reified T> T.doInBackground(crossinline onNext: T.() -> Unit,
                                        noinline onError: (Throwable) -> Unit = {},
                                        noinline onComplete: () -> Unit = {}): Disposable {
    return androidObservable()
            .subscribe({
                it.apply(onNext)
            }, {
                onError(it)
            }, {
                onComplete()
            })
}
