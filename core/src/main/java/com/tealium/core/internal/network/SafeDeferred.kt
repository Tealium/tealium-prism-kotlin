package com.tealium.core.internal.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

class SafeDeferred<T>(
    private val deferred: Deferred<T>,
    private val onCancelled: T
): Deferred<T> by deferred {
    override suspend fun await(): T {
        return try {
            deferred.await()
        } catch (ex: CancellationException) {
            onCancelled
        }
    }
}

fun <T> Deferred<T>.ensureSafeDeferred(onCancelled: T): Deferred<T> {
    return SafeDeferred(deferred = this, onCancelled = onCancelled)
}