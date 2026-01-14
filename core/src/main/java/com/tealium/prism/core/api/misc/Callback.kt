@file:JvmName("CallbackUtils")

package com.tealium.prism.core.api.misc

/**
 * Generic callback interface to use in places where a task's result will potentially be delivered
 * asynchronously.
 */
fun interface Callback<T> {

    /**
     * This method is called when the task has been completed and a [result] is therefore available.
     *
     * @param result
     */
    fun onComplete(result: T)
}

/**
 * Completes this [Callback] with a successful [TealiumResult].
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> Callback<TealiumResult<T>>.success(value: T) {
    this.onComplete(TealiumResult.success(value))
}

/**
 * Completes this [Callback] with a [TealiumResult] failure.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> Callback<TealiumResult<T>>.failure(throwable: Throwable) {
    this.onComplete(TealiumResult.failure(throwable))
}