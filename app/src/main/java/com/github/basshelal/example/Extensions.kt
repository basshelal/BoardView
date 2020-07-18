@file:Suppress("NOTHING_TO_INLINE")

package com.github.basshelal.example

import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.Transformation
import android.view.animation.TranslateAnimation

inline val now: Long get() = System.currentTimeMillis()

inline fun animation(crossinline applyTransformation:
                     (interpolatedTime: Float, transformation: Transformation) -> Unit): Animation {
    return object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            applyTransformation(interpolatedTime, t)
        }
    }
}

inline fun animationListener(crossinline onStart: (Animation) -> Unit = {},
                             crossinline onRepeat: (Animation) -> Unit = {},
                             crossinline onEnd: (Animation) -> Unit = {}) =
        object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) = onStart(animation)
            override fun onAnimationRepeat(animation: Animation) = onRepeat(animation)
            override fun onAnimationEnd(animation: Animation) = onEnd(animation)
        }

@Suppress("PROTECTED_CALL_FROM_PUBLIC_INLINE")
inline fun ScaleAnimation(
        fromX: Float, toX: Float,
        fromY: Float, toY: Float,
        pivotXType: Int, pivotXValue: Float,
        pivotYType: Int, pivotYValue: Float,
        crossinline applyTransformation:
        (interpolatedTime: Float, transformation: Transformation) -> Unit = { _, _ -> }) =
        object : ScaleAnimation(fromX, toX, fromY, toY, pivotXType, pivotXValue, pivotYType, pivotYValue) {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                super.applyTransformation(interpolatedTime, t)
                applyTransformation(interpolatedTime, t)
            }
        }

@Suppress("PROTECTED_CALL_FROM_PUBLIC_INLINE")
inline fun TranslateAnimation(
        fromXType: Int, fromXValue: Float, toXType: Int, toXValue: Float,
        fromYType: Int, fromYValue: Float, toYType: Int, toYValue: Float,
        crossinline applyTransformation:
        (interpolatedTime: Float, transformation: Transformation) -> Unit = { _, _ -> }) =
        object : TranslateAnimation(fromXType, fromXValue, toXType, toXValue, fromYType, fromYValue, toYType, toYValue) {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                super.applyTransformation(interpolatedTime, t)
                applyTransformation(interpolatedTime, t)
            }
        }

inline fun AnimationSet(vararg animations: Animation) = AnimationSet(false)
        .also { set -> animations.forEach { set.addAnimation(it) } }