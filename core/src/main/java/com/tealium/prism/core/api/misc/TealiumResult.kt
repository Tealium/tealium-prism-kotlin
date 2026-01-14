package com.tealium.prism.core.api.misc

import kotlin.Result as KResult

/**
 * Result class to support compatibility with Java
 */
class TealiumResult<T> private constructor(
    private val result: KResult<T>
) {

    /**
     * Specifies whether the result was successful
     */
    val isSuccess: Boolean
        get() = result.isSuccess

    /**
     * Specifies whether the result was a failure
     */
    val isFailure: Boolean
        get() = result.isFailure

    /**
     * Gets the result, if successful, or null if it was a failure
     */
    fun getOrNull(): T? = result.getOrNull()

    /**
     * Gets the result, if successful, or throws an exception if it was a failure
     */
    fun getOrThrow(): T = result.getOrThrow()

    /**
     * Gets the exception, if it was a failure, or null if it was successful
     */
    fun exceptionOrNull(): Throwable? = result.exceptionOrNull()

    /**
     * Executes the given [block] when the [TealiumResult] was a failure.
     *
     * @param block The block to execute when the result was a failure
     *
     * @return this [TealiumResult]
     */
    @JvmSynthetic
    inline fun onFailure(block: (ex: Throwable) -> Unit): TealiumResult<T> {
        exceptionOrNull()?.let { block(it) }
        return this
    }

    /**
     * Executes the given [block] when the [TealiumResult] was a success.
     *
     * @param block The block to execute when the result was a success
     *
     * @return this [TealiumResult]
     */
    @JvmSynthetic
    inline fun onSuccess(block: (value: T) -> Unit): TealiumResult<T> {
        if (isSuccess) block(getOrThrow())
        return this
    }

    /**
     * Executes the given [action] when the [TealiumResult] was a failure.
     *
     * @param action The callback to execute when the result was a failure
     *
     * @return this [TealiumResult]
     */
    fun onFailure(action: Callback<Throwable>): TealiumResult<T> =
        onFailure(block = { action.onComplete(it) })

    /**
     * Executes the given [action] when the [TealiumResult] was a success.
     *
     * @param action The callback to execute when the result was a success
     *
     * @return this [TealiumResult]
     */
    fun onSuccess(action: Callback<T>): TealiumResult<T> =
        onSuccess(block = { value -> action.onComplete(value) })

    companion object {
        /**
         * Creates a [TealiumResult] that was successful with the given [result] value
         */
        @JvmStatic
        fun <T> success(value: T): TealiumResult<T> = TealiumResult(KResult.success(value))

        /**
         * Creates a [TealiumResult] that was a failure with the given [throwable] as the cause
         * of the failure.
         */
        @JvmStatic
        fun <T> failure(throwable: Throwable): TealiumResult<T> = TealiumResult(KResult.failure(throwable))
    }
}