package com.github.basshelal.example

import android.view.animation.Animation
import android.view.animation.Transformation

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