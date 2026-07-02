package com.tealium.prism.core.ktx

import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Single
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until the [Single] emits its result, then returns the unwrapped success value.
 *
 * If the [TealiumResult] represents a failure, the contained exception is thrown at the call site,
 * propagating it through the coroutine's normal exception handling.
 *
 * The underlying subscription is disposed automatically when:
 * - The [Single] emits (success or failure), or
 * - The calling coroutine is canceled.
 *
 * @throws Throwable if the [TealiumResult] is a failure.
 */
suspend fun <T> Single<TealiumResult<T>>.await(): T = awaitResult().getOrThrow()

/**
 * Suspends until the [Single] emits its result, returning the full [TealiumResult].
 *
 * This is the lower-level primitive that gives callers full control over result handling —
 * useful when you want to branch on success/failure without throwing.
 *
 * The underlying subscription is disposed automatically when:
 * - The [Single] emits (success or failure), or
 * - The calling coroutine is canceled.
 */
suspend fun <T> Single<TealiumResult<T>>.awaitResult(): TealiumResult<T> =
    suspendCancellableCoroutine { continuation ->
        val disposable = subscribe(object : Observer<TealiumResult<T>> {
            override fun onNext(value: TealiumResult<T>) {
                // might only occur where a cancellation may race an emission
                if (!continuation.isActive) return

                continuation.resume(value)
            }

            override fun onComplete() {
                // Single has terminated without emitting — this should not normally happen,
                // but guard against it to avoid a suspended coroutine that never resumes.
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("Single completed without emitting a value.")
                    )
                }
            }
        })

        continuation.invokeOnCancellation {
            disposable.dispose()
        }
    }
