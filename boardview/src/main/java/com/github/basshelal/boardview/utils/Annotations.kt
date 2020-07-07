package com.github.basshelal.boardview.utils

// Experimental and will likely change in any future releases
@Retention(AnnotationRetention.SOURCE)
annotation class Beta(val reason: String = "")

// Denotes a function is called only once inside a file
// Just a marker to denote a safe inline
// Usually this is placed on a `private inline fun`
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class CalledOnce