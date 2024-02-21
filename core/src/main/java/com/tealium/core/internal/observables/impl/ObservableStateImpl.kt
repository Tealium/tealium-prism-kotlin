package com.tealium.core.internal.observables.impl

import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.StateSubject

/**
 * Default implementation of [ObservableState] that delegates all methods to the provided [StateSubject]
 */
class ObservableStateImpl<T>(
    private val subject: StateSubject<T>
) : ObservableState<T> by subject