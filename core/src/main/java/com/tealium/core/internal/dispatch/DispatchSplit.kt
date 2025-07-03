package com.tealium.core.internal.dispatch

import com.tealium.core.api.tracking.Dispatch

/**
 * Type-alias where the [Pair.first] contains the [Dispatch]es that have been successful, and the
 * [Pair.second] contains those that were unsuccessful.
 */
typealias DispatchSplit = Pair<List<Dispatch>, List<Dispatch>>
inline val DispatchSplit.successful: List<Dispatch> get() = this.first
inline val DispatchSplit.unsuccessful: List<Dispatch> get() = this.second