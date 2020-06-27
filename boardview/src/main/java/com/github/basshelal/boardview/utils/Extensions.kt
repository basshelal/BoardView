@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.boardview.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Interpolator
import android.view.animation.Transformation
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.toRectF
import androidx.core.view.children
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.github.basshelal.boardview.BuildConfig
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.jetbrains.anko.Orientation
import org.jetbrains.anko.childrenRecursiveSequence
import org.jetbrains.anko.configuration
import org.jetbrains.anko.displayMetrics
import org.jetbrains.anko.windowManager
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@PublishedApi
internal inline val Number.I: Int
    get() = this.toInt()

@PublishedApi
internal inline val Number.D: Double
    get() = this.toDouble()

@PublishedApi
internal inline val Number.F: Float
    get() = this.toFloat()

@PublishedApi
internal inline val Number.L: Long
    get() = this.toLong()

internal inline val now: Long get() = System.currentTimeMillis()

internal inline fun <K, V> HashMap<K, V>.putIfAbsentSafe(key: K, value: V) {
    if (!this.containsKey(key)) this[key] = value
}

internal inline fun View.shortSnackBar(string: String) =
        Snackbar.make(this, string, Snackbar.LENGTH_SHORT).show()

internal inline val View.locationOnScreen: Point
    get() {
        val point = IntArray(2).also {
            this.getLocationOnScreen(it)
        }
        return Point(point[0], point[1])
    }

internal inline fun View.changeParent(newParent: ViewGroup) {
    this.parentViewGroup?.removeView(this)
    newParent.addView(this)
}

internal val View.parents: List<ViewGroup>
    get() {
        val result = ArrayList<ViewGroup>()
        var current = parent
        while (current != null && current is ViewGroup) {
            result.add(current)
            current = current.parent
        }
        return result
    }

internal inline val View.parentView: View?
    get() = parent as? View?

internal inline val View.parentViewGroup: ViewGroup?
    get() = parent as? ViewGroup?

internal inline val View.rootViewGroup: ViewGroup?
    get() = this.rootView as? ViewGroup

@PublishedApi
internal inline val View.globalVisibleRect: Rect
    get() = Rect().also { this.getGlobalVisibleRect(it) }

@PublishedApi
internal inline val View.globalVisibleRectF: RectF
    get() = globalVisibleRect.toRectF()

internal inline val View.localVisibleRect: Rect
    get() = Rect().also { this.getLocalVisibleRect(it) }

internal inline val View.localVisibleRectF: RectF
    get() = localVisibleRect.toRectF()

internal inline val RectF.detailedString: String
    get() = "L: $left, T: $top, R: $right, B: $bottom"

internal inline val Rect.detailedString: String
    get() = "L: $left, T: $top, R: $right, B: $bottom"

internal inline val View.millisPerFrame get() = 1000F / context.windowManager.defaultDisplay.refreshRate

// Screen width that the context is able to use, this doesn't include navigation bars
internal inline val View.usableScreenWidth: Int get() = context.displayMetrics.widthPixels

// Screen height that the context is able to use, this doesn't include navigation bars
internal inline val View.usableScreenHeight: Int get() = context.displayMetrics.heightPixels

internal inline val View.realScreenWidth: Int
    get() = Point().also {
        context.windowManager.defaultDisplay.getRealSize(it)
    }.x

internal inline val View.realScreenHeight: Int
    get() = Point().also {
        context.windowManager.defaultDisplay.getRealSize(it)
    }.y

class LogarithmicInterpolator : Interpolator {
    override fun getInterpolation(input: Float) = log2(input + 1)
}

internal inline operator fun Interpolator.get(float: Float) = this.getInterpolation(float)

internal inline val View.orientation: Orientation
    get() = when (context.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> Orientation.LANDSCAPE
        Configuration.ORIENTATION_PORTRAIT -> Orientation.PORTRAIT
        else -> if (usableScreenHeight >= usableScreenWidth)
            Orientation.PORTRAIT else Orientation.LANDSCAPE
    }

internal inline val ViewGroup.allChildren: List<View>
    get() = this.childrenRecursiveSequence().toList()

internal inline val View.marginedWidth: Int
    get() = width + marginLeft + marginRight

internal inline fun ViewGroup.childUnder(x: Float, y: Float): View? {
    // Copied from RecyclerView.findChildViewUnder()
    children.toList().asReversed().forEach {
        if (x >= (it.left + it.translationX) &&
                x <= (it.right + it.translationX) &&
                y >= (it.top + it.translationY) &&
                y <= (it.bottom + it.translationY)) {
            return it
        }
    }
    return null
}

@PublishedApi
internal inline fun ViewGroup.forEachReversed(action: (view: View) -> Unit) {
    for (index in childCount downTo 0) {
        getChildAt(index)?.also(action)
    }
}

internal inline fun View.updateLayoutParamsSafe(block: ViewGroup.LayoutParams.() -> Unit) {
    layoutParams?.apply(block)
    requestLayout()
}

@JvmName("updateLayoutParamsSafeTyped")
internal inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParamsSafe(block: T.() -> Unit) {
    (layoutParams as? T)?.apply(block)
    requestLayout()
}

internal inline fun RecyclerView.findChildViewUnderRaw(pointF: PointF): View? {
    val rect = this.globalVisibleRectF
    val x = pointF.x - rect.left
    val y = pointF.y - rect.top
    this.forEachReversed {
        // This takes margins into account so the bounds box is larger
        if (x >= (it.left + it.translationX - it.marginLeft) &&
                x <= (it.right + it.translationX + it.marginRight) &&
                y >= (it.top + it.translationY - it.marginTop) &&
                y <= (it.bottom + it.translationY + it.marginBottom)) {
            return it
        }
    }
    return null
}

internal inline fun RecyclerView.addOnScrollListener(
        crossinline onScrollStateChanged: (newState: Int) -> Unit = { _ -> },
        crossinline onScrolled: (dx: Int, dy: Int) -> Unit = { _, _ -> }) {
    addOnScrollListener(onScrollListener(onScrollStateChanged, onScrolled))
}

internal inline fun RecyclerView.onScrollListener(
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


internal inline fun recycledViewPool(maxCount: Int) =
        object : RecyclerView.RecycledViewPool() {
            override fun setMaxRecycledViews(viewType: Int, max: Int) =
                    super.setMaxRecycledViews(viewType, maxCount)
        }

internal inline val RecyclerView.Adapter<*>.lastPosition: Int
    get() = this.itemCount - 1

internal inline fun RecyclerView.Adapter<*>.isAdapterPositionValid(position: Int): Boolean {
    return position >= 0 && position <= this.lastPosition
}

internal inline fun RecyclerView.Adapter<*>.isAdapterPositionNotValid(position: Int): Boolean {
    return !isAdapterPositionValid(position)
}

internal inline fun RecyclerView.Adapter<*>.notifySwapped(fromPosition: Int, toPosition: Int) {
    notifyItemRemoved(fromPosition)
    notifyItemInserted(fromPosition)
    notifyItemRemoved(toPosition)
    notifyItemInserted(toPosition)
}

internal inline fun RecyclerView.isViewPartiallyVisible(view: View): Boolean =
        this.layoutManager?.isViewPartiallyVisible(view, false, true) ?: false

internal inline fun RecyclerView.isViewCompletelyVisible(view: View): Boolean =
        this.layoutManager?.isViewPartiallyVisible(view, true, true) ?: false

internal inline fun RecyclerView.isViewHolderPartiallyVisible(vh: RecyclerView.ViewHolder) =
        isViewPartiallyVisible(vh.itemView)

internal inline fun RecyclerView.isViewHolderCompletelyVisible(vh: RecyclerView.ViewHolder) =
        isViewCompletelyVisible(vh.itemView)

internal inline val RecyclerView.horizontalScrollOffset: Int get() = computeHorizontalScrollOffset()
internal inline val RecyclerView.verticalScrollOffset: Int get() = computeVerticalScrollOffset()
internal inline val RecyclerView.maxHorizontalScroll: Int get() = computeHorizontalScrollRange() - computeHorizontalScrollExtent()
internal inline val RecyclerView.maxVerticalScroll: Int get() = computeVerticalScrollRange() - computeVerticalScrollExtent()

internal inline fun RecyclerView.doOnFinishScroll(
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

internal inline val RecyclerView.canScrollVertically: Boolean
    get() = this.canScrollVertically(-1) || this.canScrollVertically(1)

internal inline val RecyclerView.canScrollHorizontally: Boolean
    get() = this.canScrollHorizontally(-1) || this.canScrollHorizontally(1)

internal inline val RecyclerView.firstVisibleViewHolder: RecyclerView.ViewHolder?
    get() = (this.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.takeIf { it.isValidAdapterPosition }
            ?.let { this.findViewHolderForAdapterPosition(it) }

internal inline val RecyclerView.lastVisibleViewHolder: RecyclerView.ViewHolder?
    get() = (this.layoutManager as? LinearLayoutManager)
            ?.findLastVisibleItemPosition()
            ?.takeIf { it.isValidAdapterPosition }
            ?.let { this.findViewHolderForAdapterPosition(it) }

internal inline val RecyclerView.ViewHolder.isAdapterPositionValid: Boolean
    get() = adapterPosition != NO_POSITION

internal inline val Number.isValidAdapterPosition: Boolean
    get() = this.I >= 0

internal inline fun animation(crossinline applyTransformation:
                              (interpolatedTime: Float, transformation: Transformation) -> Unit): Animation {
    return object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            applyTransformation(interpolatedTime, t)
        }
    }
}

inline fun animationListener(crossinline onStart: (Animation) -> Unit,
                             crossinline onRepeat: (Animation) -> Unit,
                             crossinline onEnd: (Animation) -> Unit) =
        object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) = onStart(animation)
            override fun onAnimationRepeat(animation: Animation) = onRepeat(animation)
            override fun onAnimationEnd(animation: Animation) = onEnd(animation)
        }


internal inline fun logE(message: Any?, tag: String = "BoardView") {
    if (BuildConfig.DEBUG) Log.e(tag, message.toString())
}

internal inline fun View.logS(message: Any?, tag: String = "BoardView") {
    if (BuildConfig.DEBUG) Snackbar.make(this, message.toString(), Snackbar.LENGTH_SHORT).show()
}

internal inline fun FragmentManager.commitTransaction(block: FragmentTransaction.() -> Unit) {
    this.beginTransaction().apply(block).commit()
}

internal inline fun ComponentActivity.addOnBackPressedCallback(crossinline onBackPressed: () -> Unit) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = onBackPressed()
    })
}

internal inline fun MotionEvent.cancel(): MotionEvent =
        this.also { it.action = MotionEvent.ACTION_CANCEL }

internal inline infix fun Context.dpToPx(dp: Number): Float =
        (dp.F * (this.resources.displayMetrics.densityDpi.F / DisplayMetrics.DENSITY_DEFAULT))

internal inline infix fun Context.pxToDp(px: Number): Float =
        (px.F / (this.resources.displayMetrics.densityDpi.F / DisplayMetrics.DENSITY_DEFAULT))

internal inline fun RectF.verticalPercent(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.y - min) / (max - min)) * 100F)
}

internal inline fun RectF.verticalPercentInverted(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.y - max) / (min - max)) * 100F)
}

internal inline fun RectF.horizontalPercent(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.x - min) / (max - min)) * 100F)
}

internal inline fun RectF.horizontalPercentInverted(pointF: PointF): Float {
    val min = min(bottom, top)
    val max = max(bottom, top)
    return (((pointF.y - max) / (min - max)) * 100F)
}

internal inline fun RectF.copy(block: RectF.() -> Unit) = RectF(this).apply(block)

internal inline fun RectF.show(view: View, color: Int = randomColor) {
    view.rootViewGroup?.addView(
            View(view.context).also {
                it.x = this.left
                it.y = this.top
                it.layoutParams = ViewGroup.LayoutParams(this.width().I, this.height().I)
                it.setBackgroundColor(color)
                it.alpha = 0.5F
                it.requestLayout()
            }
    )
}

internal inline fun PointF.copy(block: PointF.() -> Unit) = PointF(this.x, this.y).apply(block)

internal inline fun <T> List<T>.reversedForEach(action: (T) -> Unit) {
    this.asReversed().forEach(action)
}

internal inline fun <T> List<T>.reversedForEachIndexed(action: (index: Int, T) -> Unit) {
    this.asReversed().forEachIndexed(action)
}

internal inline val randomColor: Int
    get() = Color.rgb(
            Random.nextInt(0, 256),
            Random.nextInt(0, 256),
            Random.nextInt(0, 256)
    )

internal inline fun <T> Subscriber(
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

internal inline fun <T> Observer(
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

internal inline fun <T> Observable<T>.onAndroid() = this.observeOn(AndroidSchedulers.mainThread())

/**
 * Provides an Observable that can be used to execute code on the computation thread and notify
 * the main thread, this means this Observable can be used to safely modify any UI elements on a
 * background thread
 */
internal inline fun <T> T.androidObservable(): Observable<T> {
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
internal inline fun <reified T> T.doInBackgroundWhen(crossinline predicate: (T) -> Boolean,
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
internal inline fun <reified T> T.doInBackgroundOnceWhen(crossinline predicate: (T) -> Boolean,
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

internal inline fun <reified T> T.doInBackgroundAsync(crossinline onNext: T.() -> Unit): Disposable {
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

internal inline fun <reified T> T.doInBackground(crossinline onNext: T.() -> Unit): Disposable {
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

internal inline fun <reified T> T.doInBackgroundDelayed(delayMillis: Long,
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

internal inline fun <reified T> T.doInBackground(crossinline onNext: T.() -> Unit,
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
