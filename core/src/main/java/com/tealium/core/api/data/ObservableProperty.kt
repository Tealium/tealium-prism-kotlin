package com.tealium.core.api.data

import com.tealium.core.api.Subscribable

/**
 * A read-only property that can be fetched, or observed.
 *
 * Typically used for public properties to restrict access to updates.
 * [MutableObservableProperty] is used for allowing updates to the value
 *
 * @param T - Type of the property value
 * @param L - Associated listener/observer type for handling updates
 */
interface ObservableProperty<T, L>: Subscribable<L> {

    /**
     * Retrieves the current value of the property
     */
    fun get(): T
}