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
