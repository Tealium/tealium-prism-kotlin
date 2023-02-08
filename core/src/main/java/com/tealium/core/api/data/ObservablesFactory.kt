package com.tealium.core.api.data

import java.util.concurrent.ExecutorService

/**
 * Factory for creating [MutableObservableProperty] values.
 */
interface ObservablesFactory {

    /**
     * Creates a [MutableObservableProperty] with the [initial] value and
     * the handler function [deliver] for delivering updates to any observers
     *
     * @param initial - The initial value to set for the observable property
     * @param size - The number of values to keep in the buffer to deliver to late subscribers
     * @param deliver - The on-update handler to pass updates to the observers, where [L] is the
     * the listener, and [T] is the new value
     */
    fun <T, L> createBufferedProperty(initial: T, size: Int, deliver: (L, T) -> Unit, block: (Builder<T, L>.() -> Unit)? = null): MutableObservableProperty<T, L>

    /**
     * Creates a [MutableObservableProperty] with the [initial] value and
     * the handler function [deliver] for delivering updates to any observers
     *
     * @param initial - The initial value to set for the observable property
     * @param deliver - The on-update handler to pass updates to the observers, where [L] is the
     * the listener, and [T] is the new value
     */
    fun <T, L> createProperty(initial: T, deliver: (L, T) -> Unit, block: (Builder<T, L>.() -> Unit)? = null): MutableObservableProperty<T, L>

    /**
     * Observable Builder
     */
    interface Builder<T, L> {

        /**
         * Sets the executor service to use when notifying observers
         *
         * @param executorService - the executor service to use to notify observers
         * Setting to null will execute the notification on the publishing thread.
         */
        fun executor(executorService: ExecutorService?): Builder<T, L>

        /**
         * Sets the on-update handler for delivering new values
         * to any observers
         *
         * @param onUpdate - the function to deliver updates to each observer
         * where [L] is the observer, and [T] is the new value
         */
        fun onUpdate(onUpdate: ((L, T) -> Unit)): Builder<T, L>

        /**
         * Sets the buffer size, if any
         *
         * @param size - the number of values to store in the buffer
         * to provide to new subscribers
         */
        fun buffer(size: Int): Builder<T, L>

        /**
         * Returns a mutable observable property based on the config
         * provided
         */
        fun build(): MutableObservableProperty<T, L>
    }
}