package com.tealium.core.api

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