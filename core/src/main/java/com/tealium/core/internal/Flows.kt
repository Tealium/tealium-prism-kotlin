package com.tealium.core.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Kotlin version of this function (flatMapConcat) was marked as experimental.
 *
 * @see [kotlinx.coroutines.flow.flatMapConcat]
 */
fun <T, R> Flow<T>.flatMap(transform: suspend (value: T) -> Flow<R>): Flow<R> =
    map(transform).flatten()

/**
 * Kotlin version of this function (flattenConcat) was marked as experimental.
 *
 * @see [kotlinx.coroutines.flow.flattenConcat]
 */
fun <T> Flow<Flow<T>>.flatten(): Flow<T> = flow {
    collect { emitAll(it) }
}

/**
 * Kotlin version of this function (mapLatest) was marked as experimental.
 *
 * @see [kotlinx.coroutines.flow.mapLatest]
 */
fun <T, R> Flow<T>.mapLatest(transform: suspend (value: T) -> R): Flow<R> = channelFlow {
    collectLatest {
        send(transform(it))
    }
}

/**
 * Kotlin version of this function (flatMapLatest) was marked as experimental.
 *
 * @see [kotlinx.coroutines.flow.flatMapLatest]
 */
fun <T, R> Flow<T>.flatMapLatest(transform: suspend (value: T) -> Flow<R>): Flow<R> = channelFlow {
    collectLatest { upstream ->
        transform(upstream).collectLatest { transformed ->
            send(transformed)
        }
    }
}