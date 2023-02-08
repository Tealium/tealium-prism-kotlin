package com.tealium.core.api.data

/**
 * A mutable property that can be fetched, or observed.
 * Typically for internal use, public properties should normally use [ObservableProperty]
 *
 * @param T - Type of the property value
 * @param L - Associated listener/observer type for handling updates
 */
interface MutableObservableProperty<T, L> : ObservableProperty<T, L> {

    /**
     * Updates the current value of the property, and notifies any observers
     */
    fun update(value: T)
}